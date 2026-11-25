package com.feixue.tv.data.model

import com.google.gson.annotations.SerializedName

data class VodResponse(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("page") val page: String? = null,
    @SerializedName("pagecount") val pagecount: Int = 0,
    @SerializedName("limit") val limit: String? = null,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("list") val list: List<VodDto> = emptyList(),
    @SerializedName("class") val categories: List<CategoryDto>? = null,
)

data class VodDto(
    @SerializedName("vod_id") val vodId: Int = 0,
    @SerializedName("type_id") val typeId: Int = 0,
    @SerializedName("type_name") val typeName: String? = null,
    @SerializedName("vod_name") val vodName: String? = null,
    @SerializedName("vod_pic") val vodPic: String? = null,
    @SerializedName("vod_remarks") val vodRemarks: String? = null,
    @SerializedName("vod_year") val vodYear: String? = null,
    @SerializedName("vod_area") val vodArea: String? = null,
    @SerializedName("vod_lang") val vodLang: String? = null,
    @SerializedName("vod_type") val vodType: String? = null,
    @SerializedName("vod_class") val vodClass: String? = null,
    @SerializedName("vod_content") val vodContent: String? = null,
    @SerializedName("vod_actor") val vodActor: String? = null,
    @SerializedName("vod_director") val vodDirector: String? = null,
    @SerializedName("vod_play_from") val vodPlayFrom: String? = null,
    @SerializedName("vod_play_url") val vodPlayUrl: String? = null,
    @SerializedName("vod_hits") val vodHits: String? = null,
    @SerializedName("vod_score") val vodScore: String? = null,
    @SerializedName("vod_level") val vodLevel: String? = null,
) {
    val scoreValue: Float get() = vodScore?.toFloatOrNull() ?: 0f
    val hitsValue: Long get() = vodHits?.toLongOrNull() ?: 0L
    val levelValue: Int get() = vodLevel?.toIntOrNull() ?: 0

    var sourceId: String = ""
    var sourceName: String = ""
}

data class CategoryDto(
    @SerializedName("type_id") val typeId: Int = 0,
    @SerializedName("type_name") val typeName: String = "",
    @SerializedName("type_pid") val typePid: Int = 0,
)
