package com.lucas.player.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.Size
import com.lucas.player.model.Track
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * High-performance, zero-dependency Cover Art Loader with 2-level caching
 * (Memory LruCache + Android 10+ ContentResolver thumbnail pipeline).
 */
object CoverArtLoader {

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // 12.5% of available heap

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getCachedCover(track: Track, targetSizeDp: Int = 300): Bitmap? {
        val cacheKey = "${track.id}_$targetSizeDp"
        return memoryCache.get(cacheKey)
    }

    fun loadCover(
        context: Context,
        track: Track,
        targetSizeDp: Int = 300,
        onLoaded: ((Bitmap?) -> Unit)? = null
    ) {
        val trackId = track.id
        val cacheKey = "${trackId}_$targetSizeDp"

        // 1. Check Memory Cache
        val cached = memoryCache.get(cacheKey)
        if (cached != null) {
            onLoaded?.invoke(cached)
            return
        }

        // 2. Load asynchronously in thread pool
        val appContext = context.applicationContext
        executor.execute {
            val bitmap = extractCoverBitmap(appContext, track.uri, targetSizeDp)
            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
            }

            mainHandler.post {
                onLoaded?.invoke(bitmap)
            }
        }
    }

    fun getCachedPlaylistCover(coverPath: String?): Bitmap? {
        if (coverPath.isNullOrBlank()) return null
        val file = File(coverPath)
        if (!file.exists()) return null
        val cacheKey = "pl_${coverPath}_${file.lastModified()}"
        return memoryCache.get(cacheKey)
    }

    fun loadPlaylistCover(
        coverPath: String?,
        targetSizePx: Int = 600,
        onLoaded: (Bitmap?) -> Unit
    ) {
        if (coverPath.isNullOrBlank()) {
            onLoaded(null)
            return
        }
        val file = File(coverPath)
        if (!file.exists()) {
            onLoaded(null)
            return
        }
        val cacheKey = "pl_${coverPath}_${file.lastModified()}"
        val cached = memoryCache.get(cacheKey)
        if (cached != null) {
            onLoaded(cached)
            return
        }

        executor.execute {
            val bitmap = try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(coverPath, options)
                options.inSampleSize = calculateInSampleSize(options, targetSizePx, targetSizePx)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565
                BitmapFactory.decodeFile(coverPath, options)
            } catch (_: Exception) {
                null
            }

            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
            }

            mainHandler.post {
                onLoaded(bitmap)
            }
        }
    }

    fun savePlaylistCover(context: Context, playlistId: Long, sourceUri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "playlist_covers")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val targetFile = File(dir, "pl_${playlistId}.jpg")

            val resolver = context.contentResolver

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            resolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            options.inSampleSize = calculateInSampleSize(options, 600, 600)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565

            val decodedBitmap = resolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null

            val w = decodedBitmap.width
            val h = decodedBitmap.height
            val size = minOf(w, h)
            val x = (w - size) / 2
            val y = (h - size) / 2
            val squareBitmap = if (w == h) decodedBitmap else Bitmap.createBitmap(decodedBitmap, x, y, size, size)

            val finalBitmap = if (squareBitmap.width > 600) {
                Bitmap.createScaledBitmap(squareBitmap, 600, 600, true)
            } else {
                squareBitmap
            }

            FileOutputStream(targetFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }

            deletePlaylistCoverCache(targetFile.absolutePath)

            targetFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun deletePlaylistCover(coverPath: String?) {
        if (coverPath.isNullOrBlank()) return
        try {
            val file = File(coverPath)
            if (file.exists()) {
                file.delete()
            }
            deletePlaylistCoverCache(coverPath)
        } catch (_: Exception) {}
    }

    private fun deletePlaylistCoverCache(coverPath: String) {
        val snapshot = memoryCache.snapshot()
        for (key in snapshot.keys) {
            if (key.startsWith("pl_${coverPath}")) {
                memoryCache.remove(key)
            }
        }
    }

    // --- Mídia de Artista (Foto / GIF) ---

    fun saveArtistMedia(context: Context, artistName: String, sourceUri: Uri): Pair<String, Boolean>? {
        return try {
            val cleanName = artistName.trim().lowercase()
            if (cleanName.isEmpty()) return null
            val hash = cleanName.hashCode().toString().replace("-", "n")
            val dir = File(context.filesDir, "artist_covers")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val resolver = context.contentResolver
            val mimeType = resolver.getType(sourceUri)?.lowercase() ?: ""
            val uriStr = sourceUri.toString().lowercase()
            val isGif = mimeType == "image/gif" || uriStr.endsWith(".gif")

            if (isGif) {
                val targetFile = File(dir, "art_${hash}.gif")
                val oldJpg = File(dir, "art_${hash}.jpg")
                if (oldJpg.exists()) oldJpg.delete()

                resolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(targetFile).use { out ->
                        input.copyTo(out)
                    }
                } ?: return null

                deleteArtistMediaCache(targetFile.absolutePath)
                deleteArtistMediaCache(oldJpg.absolutePath)
                Pair(targetFile.absolutePath, true)
            } else {
                val targetFile = File(dir, "art_${hash}.jpg")
                val oldGif = File(dir, "art_${hash}.gif")
                if (oldGif.exists()) oldGif.delete()

                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                resolver.openInputStream(sourceUri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                }

                options.inSampleSize = calculateInSampleSize(options, 600, 600)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565

                val decodedBitmap = resolver.openInputStream(sourceUri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                } ?: return null

                val w = decodedBitmap.width
                val h = decodedBitmap.height
                val size = minOf(w, h)
                val x = (w - size) / 2
                val y = (h - size) / 2
                val squareBitmap = if (w == h) decodedBitmap else Bitmap.createBitmap(decodedBitmap, x, y, size, size)

                val finalBitmap = if (squareBitmap.width > 600) {
                    Bitmap.createScaledBitmap(squareBitmap, 600, 600, true)
                } else {
                    squareBitmap
                }

                FileOutputStream(targetFile).use { out ->
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }

                deleteArtistMediaCache(targetFile.absolutePath)
                deleteArtistMediaCache(oldGif.absolutePath)
                Pair(targetFile.absolutePath, false)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun deleteArtistMedia(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
            deleteArtistMediaCache(path)
        } catch (_: Exception) {}
    }

    private fun deleteArtistMediaCache(path: String) {
        val snapshot = memoryCache.snapshot()
        for (key in snapshot.keys) {
            if (key.startsWith("art_${path}")) {
                memoryCache.remove(key)
            }
        }
    }

    fun getCachedArtistStaticBitmap(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        val cacheKey = "art_${path}_${file.lastModified()}"
        return memoryCache.get(cacheKey)
    }

    fun loadArtistStaticBitmap(
        path: String?,
        targetSizePx: Int = 400,
        onLoaded: (Bitmap?) -> Unit
    ) {
        if (path.isNullOrBlank()) {
            onLoaded(null)
            return
        }
        val file = File(path)
        if (!file.exists()) {
            onLoaded(null)
            return
        }
        val cacheKey = "art_${path}_${file.lastModified()}"
        val cached = memoryCache.get(cacheKey)
        if (cached != null) {
            onLoaded(cached)
            return
        }

        executor.execute {
            val bitmap = try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(path, options)
                options.inSampleSize = calculateInSampleSize(options, targetSizePx, targetSizePx)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565
                BitmapFactory.decodeFile(path, options)
            } catch (_: Exception) {
                null
            }

            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
            }

            mainHandler.post {
                onLoaded(bitmap)
            }
        }
    }

    private fun extractCoverBitmap(context: Context, uri: Uri, targetSizeDp: Int): Bitmap? {
        val resolver: ContentResolver = context.contentResolver

        // Method A: Embedded ID3 APIC / FLAC Picture extraction via MediaMetadataRetriever (Primary for SAF URIs)
        try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(context, uri)
            val picture = mmr.embeddedPicture
            mmr.release()

            if (picture != null) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(picture, 0, picture.size, options)

                val reqSize = (targetSizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(128)
                options.inSampleSize = calculateInSampleSize(options, reqSize, reqSize)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565 // Half memory footprint

                return BitmapFactory.decodeByteArray(picture, 0, picture.size, options)
            }
        } catch (_: Exception) {}

        // Method B: ContentResolver thumbnail pipeline (Fallback)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val px = (targetSizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(128)
                val thumb = resolver.loadThumbnail(uri, Size(px, px), null)
                if (thumb != null) return thumb
            } catch (_: Exception) {}
        }

        return null
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun clearCache() {
        memoryCache.evictAll()
    }
}
