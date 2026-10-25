package com.lucas.player.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lucas.player.R

data class GroupItem(
    val title: String,
    val subtitle: String,
    val countText: String
)

class GroupAdapter(
    private val onGroupClick: (GroupItem) -> Unit
) : RecyclerView.Adapter<GroupAdapter.GroupViewHolder>() {

    private val items = mutableListOf<GroupItem>()

    fun submitList(newItems: List<GroupItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.itemView.setOnClickListener {
            onGroupClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvGroupTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvGroupSubtitle)
        private val tvCount: TextView = itemView.findViewById(R.id.tvGroupCount)

        fun bind(item: GroupItem) {
            tvTitle.text = item.title
            tvSubtitle.text = item.subtitle
            tvCount.text = item.countText
        }
    }
}
