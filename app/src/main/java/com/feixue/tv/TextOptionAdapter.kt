package com.feixue.tv

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TextOptionAdapter(
    private val onClick: (Int, String) -> Unit,
) : RecyclerView.Adapter<TextOptionAdapter.OptionViewHolder>() {
    private val items = mutableListOf<Pair<Int, String>>()
    private var selectedPosition = RecyclerView.NO_POSITION

    fun setItems(newItems: List<Pair<Int, String>>, selected: Int = RecyclerView.NO_POSITION) {
        items.clear()
        items.addAll(newItems)
        selectedPosition = selected
        notifyDataSetChanged()
    }

    fun setSelectedById(id: Int) {
        val index = items.indexOfFirst { it.first == id }
        if (index < 0 || index == selectedPosition) return
        val previous = selectedPosition
        selectedPosition = index
        if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous)
        notifyItemChanged(index)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder =
        OptionViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_text_option, parent, false) as TextView,
        )

    override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
        val (id, text) = items[position]
        holder.textView.text = text
        holder.textView.isSelected = position == selectedPosition
        holder.textView.setOnClickListener {
            val current = holder.bindingAdapterPosition
            if (current == RecyclerView.NO_POSITION) return@setOnClickListener
            val previous = selectedPosition
            selectedPosition = current
            if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous)
            notifyItemChanged(current)
            onClick(id, text)
        }
    }

    class OptionViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
}
