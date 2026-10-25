package com.lucas.player.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lucas.player.R
import com.lucas.player.data.CoverArtLoader
import com.lucas.player.model.Track
import java.util.Locale

class TrackAdapter(
    private val onTrackClick: (Track, Int) -> Unit,
    private val onTrackLongClick: ((Track, Int) -> Boolean)? = null
) : RecyclerView.Adapter<TrackAdapter.TrackViewHolder>() {

    private val items = mutableListOf<Track>()
    private var currentPlayingId: Long = -1L

    fun submitList(newTracks: List<Track>) {
        items.clear()
        items.addAll(newTracks)
        notifyDataSetChanged()
    }

    fun setCurrentPlayingId(id: Long) {
        if (currentPlayingId != id) {
            currentPlayingId = id
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_track, parent, false)
        return TrackViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val track = items[position]
        holder.bind(track, track.id == currentPlayingId)
        holder.itemView.setOnClickListener {
            onTrackClick(track, position)
        }
        holder.itemView.setOnLongClickListener {
            onTrackLongClick?.invoke(track, position) ?: false
        }
    }

    override fun getItemCount(): Int = items.size

    class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgThumb: ImageView = itemView.findViewById(R.id.imgTrackThumb)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTrackTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvTrackSubtitle)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvTrackDuration)

        fun bind(track: Track, isPlaying: Boolean) {
            tvTitle.text = track.title
            tvTitle.setTextColor(if (isPlaying) Color.parseColor("#00E676") else Color.WHITE)

            val tag = if (track.formatTag.isNotEmpty()) " [${track.formatTag}]" else ""
            tvSubtitle.text = "${track.artist} • ${track.album}$tag"
            tvDuration.text = formatDuration(track.durationMs)

            imgThumb.setImageResource(android.R.drawable.ic_media_play)
            CoverArtLoader.loadCover(itemView.context, track, targetSizeDp = 48, targetView = imgThumb)
        }

        private fun formatDuration(ms: Long): String {
            val totalSec = ms / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
        }
    }
}
