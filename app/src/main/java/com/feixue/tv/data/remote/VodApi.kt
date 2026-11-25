package com.feixue.tv.data.remote

import com.feixue.tv.data.model.VodResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface VodApi {
    @GET("json")
    suspend fun getVod(
        @Query("ac") ac: String = "detail",
        @Query("pg") page: Int? = null,
        @Query("t") typeId: Int? = null,
        @Query("ids") ids: String? = null,
        @Query("wd") keyword: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("year") year: String? = null,
        @Query("area") area: String? = null,
        @Query("by") by: String? = null,
        @Query("order") order: String? = null,
    ): VodResponse
}
