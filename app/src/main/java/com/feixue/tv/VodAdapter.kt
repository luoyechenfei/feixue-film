package com.feixue.tv

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.feixue.tv.data.model.VodDto
import com.feixue.tv.databinding.ItemVodBinding

class VodAdapter(
    private val onLongClick: ((VodDto) -> Unit)? = null,
    private val onClick: (VodDto) -> Unit,
) : RecyclerView.Adapter<VodAdapter.VodViewHolder>() {
    private val items = mutableListOf<VodDto>()

    fun setItems(newItems: List<VodDto>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun appendItems(newItems: List<VodDto>) {
        if (newItems.isEmpty()) return
        val start = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(start, newItems.size)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VodViewHolder =
        VodViewHolder(ItemVodBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VodViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class VodViewHolder(
        private val binding: ItemVodBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onClick(items[position])
            }
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && onLongClick != null) {
                    onLongClick.invoke(items[position])
                    true
                } else {
                    false
                }
            }
            binding.root.setOnFocusChangeListener { view, focused ->
                view.animate()
                    .scaleX(if (focused) 1.045f else 1f)
                    .scaleY(if (focused) 1.045f else 1f)
                    .setDuration(120)
                    .start()
                view.translationZ = if (focused) 8f else 0f
            }
        }

        fun bind(vod: VodDto) = with(binding) {
            titleText.text = vod.vodName?.takeIf { it.isNotBlank() } ?: "未命名影片"
            val meta = vod.vodRemarks?.takeIf { it.isNotBlank() }
                ?: listOfNotNull(vod.vodYear, vod.vodType ?: vod.typeName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            remarkText.text = if (vod.sourceName.isNotBlank()) "[${vod.sourceName}] $meta" else meta
            sourceBadge.visibility = if (vod.sourceName.isNotBlank()) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
            sourceBadge.text = vod.sourceName
            posterImage.load(vod.vodPic?.takeIf { it.isNotBlank() }) {
                placeholder(R.drawable.bg_poster_placeholder)
                error(R.drawable.bg_poster_placeholder)
                fallback(R.drawable.bg_poster_placeholder)
                crossfade(true)
            }
        }
    }
}
