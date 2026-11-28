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

    // --- Logo PNG Transparente de Artista ---

    fun saveArtistLogo(context: Context, artistName: String, sourceUri: Uri): String? {
        return try {
            val cleanName = artistName.trim().lowercase()
            if (cleanName.isEmpty()) return null
            val hash = cleanName.hashCode().toString().replace("-", "n")
            val dir = File(context.filesDir, "artist_logos")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val targetFile = File(dir, "logo_${hash}.png")
            val resolver = context.contentResolver

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            resolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            options.inSampleSize = calculateInSampleSize(options, 800, 800)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val decodedBitmap = resolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null

            val finalBitmap = if (decodedBitmap.width > 800) {
                val targetHeight = ((decodedBitmap.height.toFloat() / decodedBitmap.width.toFloat()) * 800f)
                    .toInt()
                    .coerceAtLeast(1)
                Bitmap.createScaledBitmap(decodedBitmap, 800, targetHeight, true)
            } else {
                decodedBitmap
            }

            FileOutputStream(targetFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            deleteArtistLogoCache(targetFile.absolutePath)
            targetFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun deleteArtistLogo(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
            deleteArtistLogoCache(path)
        } catch (_: Exception) {}
    }

    private fun deleteArtistLogoCache(path: String) {
        val snapshot = memoryCache.snapshot()
        for (key in snapshot.keys) {
            if (key.startsWith("logo_${path}")) {
                memoryCache.remove(key)
            }
        }
    }

    fun getCachedArtistLogo(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        val cacheKey = "logo_${path}_${file.lastModified()}"
        return memoryCache.get(cacheKey)
    }

    fun loadArtistLogo(
        path: String?,
        targetMaxWidthPx: Int = 800,
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
        val cacheKey = "logo_${path}_${file.lastModified()}"
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
                if (options.outWidth > 0 && options.outHeight > 0) {
                    options.inSampleSize = calculateInSampleSize(options, targetMaxWidthPx, targetMaxWidthPx)
                    options.inJustDecodeBounds = false
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888
                    BitmapFactory.decodeFile(path, options)
                } else {
                    null
                }
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

    // --- Mídia Customizada de Álbum (Capa / GIF) ---

    fun saveAlbumMedia(context: Context, albumName: String, sourceUri: Uri): Pair<String, Boolean>? {
        return try {
            val cleanName = albumName.trim().lowercase()
            if (cleanName.isEmpty()) return null
            val hash = cleanName.hashCode().toString().replace("-", "n")
            val dir = File(context.filesDir, "album_covers")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val resolver = context.contentResolver
            val mimeType = resolver.getType(sourceUri)?.lowercase() ?: ""
            val uriStr = sourceUri.toString().lowercase()
            val isGif = mimeType == "image/gif" || uriStr.endsWith(".gif")

            if (isGif) {
                val targetFile = File(dir, "alb_${hash}.gif")
                val oldJpg = File(dir, "alb_${hash}.jpg")
                if (oldJpg.exists()) oldJpg.delete()

                resolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(targetFile).use { out ->
                        input.copyTo(out)
                    }
                } ?: return null

                deleteAlbumMediaCache(targetFile.absolutePath)
                deleteAlbumMediaCache(oldJpg.absolutePath)
                Pair(targetFile.absolutePath, true)
            } else {
                val targetFile = File(dir, "alb_${hash}.jpg")
                val oldGif = File(dir, "alb_${hash}.gif")
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

                deleteAlbumMediaCache(targetFile.absolutePath)
                deleteAlbumMediaCache(oldGif.absolutePath)
                Pair(targetFile.absolutePath, false)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun deleteAlbumMedia(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
            deleteAlbumMediaCache(path)
        } catch (_: Exception) {}
    }

    private fun deleteAlbumMediaCache(path: String) {
        val snapshot = memoryCache.snapshot()
        for (key in snapshot.keys) {
            if (key.startsWith("alb_${path}")) {
                memoryCache.remove(key)
            }
        }
    }

    fun getCachedAlbumStaticBitmap(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        val cacheKey = "alb_${path}_${file.lastModified()}"
        return memoryCache.get(cacheKey)
    }

    fun loadAlbumStaticBitmap(
        path: String?,
        targetSizePx: Int = 600,
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
        val cacheKey = "alb_${path}_${file.lastModified()}"
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
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            val picture = mmr.embeddedPicture

            if (picture != null && picture.isNotEmpty()) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(picture, 0, picture.size, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    val reqSize = (targetSizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(128)
                    options.inSampleSize = calculateInSampleSize(options, reqSize, reqSize)
                    options.inJustDecodeBounds = false
                    options.inPreferredConfig = Bitmap.Config.RGB_565 // Half memory footprint

                    return BitmapFactory.decodeByteArray(picture, 0, picture.size, options)
                }
            }
        } catch (_: Throwable) {
        } finally {
            try {
                mmr.release()
            } catch (_: Throwable) {}
        }

        // Method B: ContentResolver thumbnail pipeline (Fallback)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val px = (targetSizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(128)
                val thumb = resolver.loadThumbnail(uri, Size(px, px), null)
                if (thumb != null) return thumb
            } catch (_: Throwable) {}
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

    // --- Mídia Dual Unificada (GIF + Imagem Estática + Frame Congelado) ---

    private fun entityHash(entityType: String, entityKey: String): String {
        val norm = "${entityType.trim().uppercase()}_${entityKey.trim().lowercase()}"
        return norm.hashCode().toString().replace("-", "n")
    }

    fun saveCustomGif(context: Context, entityType: String, entityKey: String, sourceUri: Uri): String? {
        return try {
            if (entityKey.isBlank()) return null
            val dir = File(context.filesDir, "custom_media")
            if (!dir.exists()) dir.mkdirs()
            val hash = entityHash(entityType, entityKey)
            val stamp = System.currentTimeMillis()
            val targetFile = File(dir, "gif_${hash}_${stamp}.gif")

            // Remove versões anteriores deste mesmo slot para evitar lixo em disco
            dir.listFiles()?.filter { it.name.startsWith("gif_${hash}_") }?.forEach { old ->
                deleteFileMediaCache(old.absolutePath)
                old.delete()
            }

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { out ->
                    input.copyTo(out)
                }
            } ?: return null

            deleteFileMediaCache(targetFile.absolutePath)
            targetFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun saveCustomStaticImage(context: Context, entityType: String, entityKey: String, sourceUri: Uri): String? {
        return try {
            if (entityKey.isBlank()) return null
            val dir = File(context.filesDir, "custom_media")
            if (!dir.exists()) dir.mkdirs()
            val hash = entityHash(entityType, entityKey)
            val stamp = System.currentTimeMillis()
            val targetFile = File(dir, "img_${hash}_${stamp}.jpg")

            dir.listFiles()?.filter { it.name.startsWith("img_${hash}_") }?.forEach { old ->
                deleteFileMediaCache(old.absolutePath)
                old.delete()
            }

            val resolver = context.contentResolver
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            resolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            options.inSampleSize = calculateInSampleSize(options, 900, 900)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565

            val decodedBitmap = resolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null

            val maxDim = maxOf(decodedBitmap.width, decodedBitmap.height)
            val finalBitmap = if (maxDim > 900) {
                val scale = 900f / maxDim.toFloat()
                val w = (decodedBitmap.width * scale).toInt().coerceAtLeast(1)
                val h = (decodedBitmap.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decodedBitmap, w, h, true)
            } else {
                decodedBitmap
            }

            FileOutputStream(targetFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            deleteFileMediaCache(targetFile.absolutePath)
            targetFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun deleteCustomFile(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
            deleteFileMediaCache(path)
        } catch (_: Exception) {}
    }

    private fun deleteFileMediaCache(path: String) {
        val snapshot = memoryCache.snapshot()
        for (key in snapshot.keys) {
            if (key.contains(path)) {
                memoryCache.remove(key)
            }
        }
    }

    fun getCachedFileBitmap(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        val cacheKey = "file_${path}_${file.lastModified()}"
        return memoryCache.get(cacheKey)
    }

    fun loadFileBitmap(
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
        val cacheKey = "file_${path}_${file.lastModified()}"
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
                if (options.outWidth > 0 && options.outHeight > 0) {
                    options.inSampleSize = calculateInSampleSize(options, targetSizePx, targetSizePx)
                    options.inJustDecodeBounds = false
                    options.inPreferredConfig = Bitmap.Config.RGB_565
                    BitmapFactory.decodeFile(path, options)
                } else {
                    null
                }
            } catch (_: Throwable) {
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

    @Suppress("DEPRECATION")
    fun extractGifFrameSamples(gifPath: String, maxSamples: Int = 20, thumbSizePx: Int = 128): List<Bitmap> {
        val file = File(gifPath)
        if (!file.exists()) return emptyList()
        val count = maxSamples.coerceIn(4, 24)
        return try {
            val movie = android.graphics.Movie.decodeFile(gifPath)
            if (movie != null && movie.width() > 0 && movie.height() > 0) {
                val duration = movie.duration().let { if (it > 0) it else 1000 }
                val mw = movie.width().toFloat()
                val mh = movie.height().toFloat()
                val scale = minOf(thumbSizePx / mw, thumbSizePx / mh).coerceAtMost(1f)
                val outW = (mw * scale).toInt().coerceAtLeast(1)
                val outH = (mh * scale).toInt().coerceAtLeast(1)
                val frames = ArrayList<Bitmap>(count)
                for (i in 0 until count) {
                    val timeMs = if (count <= 1) 0 else ((i.toFloat() / (count - 1).toFloat()) * duration).toInt().coerceIn(0, duration)
                    val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.scale(outW / mw, outH / mh)
                    movie.setTime(timeMs)
                    movie.draw(canvas, 0f, 0f)
                    frames.add(bmp)
                }
                frames
            } else {
                val single = BitmapFactory.decodeFile(gifPath)
                if (single != null) listOf(single) else emptyList()
            }
        } catch (_: Throwable) {
            try {
                val single = BitmapFactory.decodeFile(gifPath)
                if (single != null) listOf(single) else emptyList()
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    @Suppress("DEPRECATION")
    fun saveFrozenGifFrame(
        context: Context,
        entityType: String,
        entityKey: String,
        gifPath: String,
        frameIndex: Int,
        sampleCount: Int = 20
    ): String? {
        return try {
            val gifFile = File(gifPath)
            if (!gifFile.exists()) return null
            val dir = File(context.filesDir, "frozen_frames")
            if (!dir.exists()) dir.mkdirs()
            val hash = entityHash(entityType, entityKey)
            val stamp = System.currentTimeMillis()
            val targetFile = File(dir, "ff_${hash}_${frameIndex}_${stamp}.jpg")

            dir.listFiles()?.filter { it.name.startsWith("ff_${hash}_") }?.forEach { old ->
                deleteFileMediaCache(old.absolutePath)
                old.delete()
            }

            val total = sampleCount.coerceIn(1, 24)
            val clampedIdx = frameIndex.coerceIn(0, total - 1)
            val frameBmp: Bitmap = (try {
                val movie = android.graphics.Movie.decodeFile(gifPath)
                if (movie != null && movie.width() > 0 && movie.height() > 0) {
                    val duration = movie.duration().let { if (it > 0) it else 1000 }
                    val timeMs = if (total <= 1) 0 else ((clampedIdx.toFloat() / (total - 1).toFloat()) * duration).toInt().coerceIn(0, duration)
                    val mw = movie.width().toFloat()
                    val mh = movie.height().toFloat()
                    val scale = (720f / maxOf(mw, mh)).coerceAtMost(1f)
                    val outW = (mw * scale).toInt().coerceAtLeast(1)
                    val outH = (mh * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.scale(outW / mw, outH / mh)
                    movie.setTime(timeMs)
                    movie.draw(canvas, 0f, 0f)
                    bmp
                } else {
                    BitmapFactory.decodeFile(gifPath)
                }
            } catch (_: Throwable) {
                BitmapFactory.decodeFile(gifPath)
            }) ?: return null

            FileOutputStream(targetFile).use { out ->
                frameBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            deleteFileMediaCache(targetFile.absolutePath)
            targetFile.absolutePath
        } catch (_: Throwable) {
            null
        }
    }
}
