package com.feixue.tv.data.model

import com.google.gson.annotations.SerializedName

data class DoubanSubject(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("rate") val rate: String = "",
    @SerializedName("cover") val cover: String = "",
)

data class DoubanResponse(
    @SerializedName("subjects") val subjects: List<DoubanSubject>? = null,
)
