package com.feixue.tv.data.remote

import com.feixue.tv.data.source.VideoSource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ApiClient {
    private val apis = ConcurrentHashMap<String, VodApi>()

    @Volatile
    private var lastUrl: String? = null

    @Volatile
    private var lastApi: VodApi? = null

    private val gson: Gson = createVodGson()

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun apiFor(source: VideoSource): VodApi {
        val url = source.url
        lastApi?.let { if (lastUrl == url) return it }
        val api = apis.getOrPut(url) {
            Retrofit.Builder()
                .baseUrl(url.substringBeforeLast('/') + '/')
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(VodApi::class.java)
        }
        lastUrl = url
        lastApi = api
        return api
    }
}

internal fun createVodGson(): Gson = GsonBuilder()
    .registerTypeAdapter(String::class.java, ScalarStringDeserializer())
    .create()

private class ScalarStringDeserializer : JsonDeserializer<String> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ): String? = when {
        json == null || json.isJsonNull -> null
        json.isJsonPrimitive -> json.asString
        else -> null
    }
}
