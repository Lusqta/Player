package com.seunome.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainSongAdapter(
    context: Context,
    private val songs: List<Song>
) : ArrayAdapter<Song>(context, 0, songs) {

    // Cache de memória básico para não recarregar fotos na mesma sessão
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_main_song, parent, false)

        val tvTitle = view.findViewById<TextView>(R.id.itemTitle)
        val tvArtist = view.findViewById<TextView>(R.id.itemArtist)
        val ivAlbumArt = view.findViewById<ImageView>(R.id.itemAlbumArt)

        val song = songs[position]

        tvTitle.text = song.title
        tvArtist.text = song.artist

        // Coloca o placeholder para não piscar imagem antiga no scroll
        ivAlbumArt.setImageResource(R.drawable.ic_music_note)
        ivAlbumArt.setPadding(8, 8, 8, 8)

        val uri = song.uri
        if (uri.isNotEmpty()) {
            if (bitmapCache.containsKey(uri)) {
                ivAlbumArt.setImageBitmap(bitmapCache[uri])
                ivAlbumArt.setPadding(0, 0, 0, 0)
            } else {
                // Carrega em background
                CoroutineScope(Dispatchers.IO).launch {
                    val bitmap = extractResizeBitmap(uri)
                    if (bitmap != null) {
                        bitmapCache[uri] = bitmap
                        withContext(Dispatchers.Main) {
                            // Verifica se a view ainda é da mesma música pós-scroll
                            if (tvTitle.text == song.title) {
                                ivAlbumArt.setImageBitmap(bitmap)
                                ivAlbumArt.setPadding(0, 0, 0, 0)
                            }
                        }
                    }
                }
            }
        }

        return view
    }

    private fun extractResizeBitmap(path: String): Bitmap? {
        var extractedBitmap: Bitmap? = null
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val art = retriever.embeddedPicture
            if (art != null) {
                val originalBitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                extractedBitmap = scaleBitmapToMax(originalBitmap, 128) // 128px é o suficiente pra lista inicial
            }
            retriever.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return extractedBitmap
    }

    private fun scaleBitmapToMax(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxSize && height <= maxSize) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val newWidth = if (ratio > 1) maxSize else (maxSize * ratio).toInt()
        val newHeight = if (ratio > 1) (maxSize / ratio).toInt() else maxSize

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
