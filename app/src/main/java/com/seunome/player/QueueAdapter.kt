package com.seunome.player

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class QueueAdapter(
    context: Context,
    private val songs: List<Song>,
    private val currentIndex: Int // Índice da música tocando agora na lista
) : ArrayAdapter<Song>(context, 0, songs) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_queue, parent, false)
        
        val song = songs[position]
        
        val tvTitle = view.findViewById<TextView>(R.id.tvQueueItemTitle)
        val tvArtist = view.findViewById<TextView>(R.id.tvQueueItemArtist)
        val separatorContainer = view.findViewById<LinearLayout>(R.id.separatorContainer)
        val tvSeparatorTitle = view.findViewById<TextView>(R.id.tvSeparatorTitle)
        val ivNowPlaying = view.findViewById<ImageView>(R.id.ivNowPlaying)
        
        tvTitle.text = song.title
        tvArtist.text = song.artist
        
        // Estilo baseado no status (Histórico vs Atual vs Fila)
        if (position < currentIndex) {
            // Histórico: Texto opaco
            tvTitle.setTextColor(Color.parseColor("#888888"))
            ivNowPlaying.visibility = View.GONE
            separatorContainer.visibility = View.GONE
        } else if (position == currentIndex) {
            // Tocando agora: Verdão destaque
            tvTitle.setTextColor(Color.parseColor("#1DB954"))
            ivNowPlaying.visibility = View.VISIBLE
            separatorContainer.visibility = View.GONE
        } else {
            // Fila futura: texto branco normal
            tvTitle.setTextColor(Color.parseColor("#FFFFFF"))
            ivNowPlaying.visibility = View.GONE
            
            // Só exibe o separador na PRIMEIRA música vindo depois do currentIndex
            if (position == currentIndex + 1) {
                separatorContainer.visibility = View.VISIBLE
                tvSeparatorTitle.text = "Próximos a Tocar"
            } else {
                separatorContainer.visibility = View.GONE
            }
        }
        
        // Exceção de layout: Se ele selecionou uma música solta e não tem histórico/playlist, tudo branco normal sem separador
        if (currentIndex < 0) {
            tvTitle.setTextColor(Color.parseColor("#FFFFFF"))
            ivNowPlaying.visibility = View.GONE
            separatorContainer.visibility = View.GONE
        }

        return view
    }
}
