package com.feixue.tv.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser

/** 播放历史：按数据源 + 影片 ID 存储，保留最近播放的线路与集数 */
object HistoryStore {
    private const val PREFS = "feixue_history"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 200
    private val gson = Gson()

    data class Item(
        val vodId: Int,
        val sourceId: String,
        val name: String,
        val pic: String,
        val sourceName: String,
        val episodeIndex: Int,
        val episodeName: String,
        val updatedAt: Long,
    )

    private fun read(context: Context): MutableList<Item> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "[]").orEmpty()
        val array = runCatching { JsonParser.parseString(json).asJsonArray }.getOrNull()
            ?: return mutableListOf()
        return array.mapNotNull { element ->
            runCatching { gson.fromJson(element, Item::class.java) }.getOrNull()
        }.sortedByDescending { it.updatedAt }.toMutableList()
    }

    private fun write(context: Context, items: List<Item>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, gson.toJson(items.take(MAX_ITEMS))).apply()
    }

    fun all(context: Context): List<Item> = read(context)

    fun save(context: Context, item: Item) {
        val items = read(context)
        items.removeAll { it.vodId == item.vodId && it.sourceId == item.sourceId }
        items.add(0, item)
        write(context, items)
    }

    fun remove(context: Context, vodId: Int, sourceId: String) {
        val items = read(context)
        items.removeAll { it.vodId == vodId && it.sourceId == sourceId }
        write(context, items)
    }

    fun clear(context: Context) = write(context, emptyList())
}
