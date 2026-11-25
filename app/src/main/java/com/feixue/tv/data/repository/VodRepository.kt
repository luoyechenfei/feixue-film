package com.feixue.tv.data.repository

import com.feixue.tv.data.model.CategoryDto
import com.feixue.tv.data.model.VodDto
import com.feixue.tv.data.model.VodResponse
import com.feixue.tv.data.remote.ApiClient
import com.feixue.tv.data.source.SourceManager
import com.feixue.tv.data.source.VideoSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class VodRepository(private val source: VideoSource? = null) {
    private fun api() = ApiClient.apiFor(source ?: SourceManager.active())

    suspend fun categories(): List<CategoryDto> =
        api().getVod(ac = "list").validated().categories.orEmpty()

    suspend fun home(page: Int = 1, limit: Int? = null): List<VodDto> =
        api().getVod(ac = "detail", page = page, limit = limit).validated().list

    suspend fun loadCategory(
        typeId: Int,
        page: Int = 1,
        year: String? = null,
        limit: Int? = null,
    ): List<VodDto> = api().getVod(
        ac = "detail",
        page = page,
        typeId = typeId,
        year = year,
        limit = limit,
    ).validated().list

    suspend fun search(
        keyword: String,
        page: Int = 1,
        limit: Int? = null,
    ): List<VodDto> = api().getVod(
        ac = "detail",
        page = page,
        keyword = keyword,
        limit = limit,
    ).validated().list

    suspend fun detail(id: Int): VodDto? =
        api().getVod(ac = "detail", ids = id.toString()).validated().list.firstOrNull()

    /**
     * 首页推荐：优先取网站后台标记为推荐（vod_level > 0）的影片，
     * 按推荐等级排序；若站点未设置推荐，则回退为评分、热度排序。
     */
    suspend fun recommend(limit: Int = 12): List<VodDto> = coroutineScope {
        val pages = (1..5).map { page ->
            async { runCatching { home(page) }.getOrDefault(emptyList()) }
        }.awaitAll().flatten()
        val distinct = pages.distinctBy { it.vodId }
            .filter { it.vodId > 0 && !it.vodName.isNullOrBlank() }
        val marked = distinct.filter { it.levelValue > 0 }
        val sorted = if (marked.isNotEmpty()) {
            marked.sortedWith(
                compareByDescending<VodDto> { it.levelValue }
                    .thenByDescending { it.scoreValue },
            )
        } else {
            distinct.sortedWith(
                compareByDescending<VodDto> { it.scoreValue }
                    .thenByDescending { it.hitsValue },
            )
        }
        sorted.take(limit)
    }

    private fun VodResponse.validated(): VodResponse {
        if (code != 1) throw VodApiException(code, msg)
        return this
    }
}

class VodApiException(val code: Int, message: String?) :
    Exception(message ?: "API request failed with code $code")
