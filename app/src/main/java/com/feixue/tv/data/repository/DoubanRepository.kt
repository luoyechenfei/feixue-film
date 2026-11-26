package com.feixue.tv.data.repository

import android.content.Context
import com.feixue.tv.data.model.VodDto
import com.feixue.tv.data.remote.ApiClient
import com.google.gson.Gson

/**
 * 首页推荐：直接使用豆瓣热门榜（海报、片名、评分都来自豆瓣，秒开），
 * 点击时再按片名到真实片源解析 vodId；结果落盘缓存，冷启动也立即显示。
 */
class DoubanRepository(
    context: Context? = null,
    private val vodRepository: VodRepository = VodRepository(),
) {
    private val prefs = context?.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun cachedRecommend(): List<VodDto> {
        val raw = prefs?.getString(KEY_ITEMS, null)?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching { gson.fromJson(raw, Array<VodDto>::class.java).toList() }
            .getOrDefault(emptyList())
            .filter { !it.vodName.isNullOrBlank() }
    }

    fun cacheAge(): Long {
        val savedAt = prefs?.getLong(KEY_SAVED_AT, 0L) ?: 0L
        return if (savedAt <= 0L) Long.MAX_VALUE else System.currentTimeMillis() - savedAt
    }

    /** 豆瓣热门榜，只发 1 个请求，直接返回可展示的列表 */
    suspend fun hot(limit: Int = 12): List<VodDto> {
        val subjects = ApiClient.douban()
            .hotSubjects(userAgent = USER_AGENT, referer = REFERER, pageLimit = limit)
            .subjects
            .orEmpty()
            .filter { it.title.isNotBlank() }
        val items = subjects.map { subject ->
            VodDto(
                vodId = 0,
                vodName = subject.title,
                vodPic = subject.cover.takeIf { it.isNotBlank() },
                vodScore = subject.rate.takeIf { it.isNotBlank() },
            )
        }
        if (items.isNotEmpty()) save(items)
        return items
    }

    /** 按片名在真实片源里解析可播放的 vodId */
    suspend fun resolve(title: String): Int? = runCatching {
        vodRepository.search(title, limit = 3)
    }.getOrDefault(emptyList())
        .firstOrNull { it.vodId > 0 && !it.vodPic.isNullOrBlank() }
        ?.vodId

    private fun save(items: List<VodDto>) {
        prefs?.edit()
            ?.putString(KEY_ITEMS, gson.toJson(items))
            ?.putLong(KEY_SAVED_AT, System.currentTimeMillis())
            ?.apply()
    }

    private companion object {
        const val CACHE_NAME = "feixue_cache"
        const val KEY_ITEMS = "douban_recommend"
        const val KEY_SAVED_AT = "douban_recommend_at"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val REFERER = "https://movie.douban.com/"
    }
}
