package com.feixue.tv.data.remote

import com.feixue.tv.data.model.DoubanResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface DoubanApi {
    @GET("j/search_subjects")
    suspend fun hotSubjects(
        @Header("User-Agent") userAgent: String,
        @Header("Referer") referer: String,
        @Query("type") type: String = "movie",
        @Query("tag") tag: String = "热门",
        @Query("sort") sort: String = "recommend",
        @Query("page_limit") pageLimit: Int = 12,
        @Query("page_start") pageStart: Int = 0,
    ): DoubanResponse
}
