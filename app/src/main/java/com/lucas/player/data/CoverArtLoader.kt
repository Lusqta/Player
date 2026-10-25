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
import android.widget.ImageView
import com.lucas.player.model.Track
import java.util.concurrent.Executors

/**
 * High-performance, zero-dependency Cover Art Loader with 2-level caching
 * (Memory LruCache + Android 10+ ContentResolver thumbnail pipeline).
 */
object CoverArtLoader {

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // 12.5% of available heap

    private val memoryCache = object : LruCache<Long, Bitmap>(cacheSize) {
        override fun sizeOf(key: Long, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadCover(
        context: Context,
        track: Track,
        targetSizeDp: Int = 300,
        targetView: ImageView? = null,
        onLoaded: ((Bitmap?) -> Unit)? = null
    ) {
        val trackId = track.id

        // 1. Check Memory Cache
        val cached = memoryCache.get(trackId)
        if (cached != null) {
            targetView?.setImageBitmap(cached)
            onLoaded?.invoke(cached)
            return
        }

        // Tag view to avoid mismatched bitmaps during fast RecyclerView scrolling
        targetView?.tag = trackId

        // 2. Load asynchronously in thread pool
        val appContext = context.applicationContext
        executor.execute {
            val bitmap = extractCoverBitmap(appContext, track.uri, targetSizeDp)
            if (bitmap != null) {
                memoryCache.put(trackId, bitmap)
            }

            mainHandler.post {
                if (targetView == null || targetView.tag == trackId) {
                    targetView?.setImageBitmap(bitmap)
                    onLoaded?.invoke(bitmap)
                }
            }
        }
    }

    private fun extractCoverBitmap(context: Context, uri: Uri, targetSizeDp: Int): Bitmap? {
        val resolver: ContentResolver = context.contentResolver

        // Method A: Modern Android 10+ (API 29+) hardware thumbnail pipeline
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val px = (targetSizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(128)
                val thumb = resolver.loadThumbnail(uri, Size(px, px), null)
                if (thumb != null) return thumb
            } catch (_: Exception) {}
        }

        // Method B: Embedded ID3 APIC / FLAC Picture extraction via MediaMetadataRetriever
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
