package com.feixue.tv

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.feixue.tv.data.model.VodDto
import com.feixue.tv.databinding.ItemRecommendBinding

class RecommendAdapter(
    private val onClick: (VodDto) -> Unit,
) : RecyclerView.Adapter<RecommendAdapter.RecommendViewHolder>() {
    private val items = mutableListOf<VodDto>()

    fun setItems(newItems: List<VodDto>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecommendViewHolder =
        RecommendViewHolder(
            ItemRecommendBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

    override fun onBindViewHolder(holder: RecommendViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class RecommendViewHolder(
        private val binding: ItemRecommendBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onClick(items[position])
            }
            binding.root.setOnFocusChangeListener { view, focused ->
                view.animate()
                    .scaleX(if (focused) 1.06f else 1f)
                    .scaleY(if (focused) 1.06f else 1f)
                    .setDuration(120)
                    .start()
                view.translationZ = if (focused) 10f else 0f
            }
        }

        fun bind(vod: VodDto) = with(binding) {
            titleText.text = vod.vodName?.takeIf { it.isNotBlank() } ?: "未命名影片"
            scoreText.text = buildList {
                if (vod.scoreValue > 0f) add("评分 ${vod.scoreValue}")
                vod.vodRemarks?.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(" · ")
            posterImage.load(vod.vodPic?.takeIf { it.isNotBlank() }) {
                placeholder(R.drawable.bg_poster_placeholder)
                error(R.drawable.bg_poster_placeholder)
                fallback(R.drawable.bg_poster_placeholder)
                crossfade(true)
            }
        }
    }
}
