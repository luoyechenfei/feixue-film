package com.feixue.tv

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.feixue.tv.data.source.VideoSource
import com.feixue.tv.databinding.ItemSourceBinding

class SourceAdapter(
    private val onClick: (VideoSource) -> Unit,
) : RecyclerView.Adapter<SourceAdapter.SourceViewHolder>() {
    private val items = mutableListOf<VideoSource>()
    private var activeId: String = ""

    fun submit(list: List<VideoSource>, active: String) {
        items.clear()
        items.addAll(list)
        activeId = active
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceViewHolder =
        SourceViewHolder(
            ItemSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

    override fun onBindViewHolder(holder: SourceViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class SourceViewHolder(
        private val binding: ItemSourceBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onClick(items[position])
            }
            binding.root.setOnFocusChangeListener { view, focused ->
                view.animate()
                    .scaleX(if (focused) 1.01f else 1f)
                    .scaleY(if (focused) 1.01f else 1f)
                    .setDuration(100)
                    .start()
            }
        }

        fun bind(source: VideoSource) = with(binding) {
            sourceName.text = source.name
            sourceUrl.text = source.url
            sourceBadge.text = when {
                source.id == activeId && !source.enabled -> "● 使用中 · 未聚合"
                source.id == activeId -> "● 使用中"
                !source.enabled -> "已停用"
                source.builtin -> "自带"
                else -> ""
            }
        }
    }
}
