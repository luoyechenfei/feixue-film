package com.feixue.tv.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser

/** 本地收藏：按数据源 + 影片 ID 存储 */
object FavoriteStore {
    private const val PREFS = "feixue_favorites"
    private const val KEY_ITEMS = "items"
    private val gson = Gson()

    data class Item(
        val vodId: Int,
        val sourceId: String,
        val name: String,
        val pic: String,
        val remark: String,
    )

    private fun read(context: Context): MutableList<Item> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "[]").orEmpty()
        val array = runCatching { JsonParser.parseString(json).asJsonArray }.getOrNull()
            ?: return mutableListOf()
        return array.mapNotNull { element ->
            runCatching { gson.fromJson(element, Item::class.java) }.getOrNull()
        }.toMutableList()
    }

    private fun write(context: Context, items: List<Item>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, gson.toJson(items)).apply()
    }

    fun all(context: Context): List<Item> = read(context)

    fun clear(context: Context) = write(context, emptyList())

    fun contains(context: Context, vodId: Int, sourceId: String): Boolean =
        read(context).any { it.vodId == vodId && it.sourceId == sourceId }

    /** 切换收藏状态，返回切换后是否已收藏 */
    fun toggle(context: Context, item: Item): Boolean {
        val items = read(context)
        val existing = items.indexOfFirst { it.vodId == item.vodId && it.sourceId == item.sourceId }
        return if (existing >= 0) {
            items.removeAt(existing)
            write(context, items)
            false
        } else {
            items.add(0, item)
            write(context, items)
            true
        }
    }
}
