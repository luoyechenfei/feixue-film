package com.feixue.tv.data.source

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.util.UUID

object SourceManager {
    const val DEFAULT_URL = "http://nas.luoyechenfei.com:8100/api.php/provide/vod/at/json"

    private const val PREFS_NAME = "feixue_sources"
    private const val KEY_LIST = "source_list"
    private const val KEY_ACTIVE = "active_source_id"

    private val gson = Gson()
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (readList().isEmpty()) {
                val builtin = VideoSource(
                    id = "builtin",
                    name = "飞雪自带源",
                    url = DEFAULT_URL,
                    builtin = true,
                )
                writeList(listOf(builtin))
                prefs.edit().putString(KEY_ACTIVE, builtin.id).apply()
            }
        }
    }

    fun all(): List<VideoSource> = readList()

    fun enabledSources(): List<VideoSource> = readList().filter { it.enabled }

    fun byId(id: String): VideoSource? = readList().firstOrNull { it.id == id }

    fun active(): VideoSource {
        val list = readList()
        val activeId = prefs.getString(KEY_ACTIVE, null)
        return list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
            ?: VideoSource("builtin", "飞雪自带源", DEFAULT_URL, true)
    }

    fun activeId(): String = active().id

    fun setActive(id: String) {
        if (readList().any { it.id == id }) {
            prefs.edit().putString(KEY_ACTIVE, id).apply()
        }
    }

    fun upsert(source: VideoSource) {
        val list = readList().toMutableList()
        val index = list.indexOfFirst { it.id == source.id }
        if (index >= 0) list[index] = source else list.add(source)
        writeList(list)
        if (prefs.getString(KEY_ACTIVE, null) == null) {
            prefs.edit().putString(KEY_ACTIVE, source.id).apply()
        }
    }

    fun delete(id: String): Boolean {
        val list = readList().toMutableList()
        if (list.size <= 1) return false
        if (list.removeAll { it.id == id }) {
            writeList(list)
            if (prefs.getString(KEY_ACTIVE, null) == id) {
                prefs.edit().putString(KEY_ACTIVE, list.first().id).apply()
            }
            return true
        }
        return false
    }

    fun createId(): String = "src-" + UUID.randomUUID().toString().take(8)

    fun normalizeUrl(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return url
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            url = "http://$url"
        }
        url = url.substringBefore('?').trimEnd('/')
        return when {
            url.endsWith("/json", true) -> url
            url.endsWith("/at", true) -> "$url/json"
            url.endsWith("/vod", true) -> "$url/at/json"
            url.endsWith(".php", true) -> "$url/at/json"
            else -> "$url/at/json"
        }
    }

    /**
     * 支持三种订阅格式：
     * 1. JSON 数组 [{"name":"源名","url":"接口地址"}]
     * 2. 每行 "源名,接口地址" 或 "源名|接口地址" 或 "源名 接口地址"
     * 3. 每行一个接口地址
     */
    fun parseSubscription(text: String): List<VideoSource> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.startsWith("[")) {
            val result = mutableListOf<VideoSource>()
            runCatching {
                val array = JsonParser.parseString(trimmed).asJsonArray
                array.forEach { element ->
                    val obj = element.asJsonObject
                    val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString
                    val url = obj.get("url")?.takeIf { !it.isJsonNull }?.asString
                        ?: obj.get("api")?.takeIf { !it.isJsonNull }?.asString
                    if (!url.isNullOrBlank()) {
                        result.add(
                            VideoSource(
                                id = createId(),
                                name = name?.takeIf { it.isNotBlank() } ?: "第三方源",
                                url = normalizeUrl(url),
                            ),
                        )
                    }
                }
            }
            if (result.isNotEmpty()) return result
        }

        val result = mutableListOf<VideoSource>()
        trimmed.lineSequence().forEach { line ->
            val item = line.trim()
            if (item.isEmpty() || item.startsWith("#")) return@forEach
            val separator = when {
                item.contains('|') -> '|'
                item.contains(',') -> ','
                item.contains(' ') -> ' '
                else -> null
            }
            var name: String? = null
            var url = item
            if (separator != null) {
                val parts = item.split(separator, limit = 2)
                if (parts.size == 2 && parts[1].trim().startsWith("http", true)) {
                    name = parts[0].trim()
                    url = parts[1].trim()
                }
            }
            if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
                result.add(
                    VideoSource(
                        id = createId(),
                        name = name?.takeIf { it.isNotBlank() } ?: "第三方源",
                        url = normalizeUrl(url),
                    ),
                )
            }
        }
        return result
    }

    private fun readList(): List<VideoSource> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return runCatching {
            val array = JsonParser.parseString(raw).asJsonArray
            array.mapNotNull { element ->
                val obj = element.asJsonObject
                val id = obj.get("id")?.asString ?: return@mapNotNull null
                val name = obj.get("name")?.asString ?: "未命名源"
                val url = obj.get("url")?.asString ?: return@mapNotNull null
                val builtin = obj.get("builtin")?.asBoolean ?: false
                val enabled = obj.get("enabled")?.asBoolean ?: true
                VideoSource(id, name, url, builtin, enabled)
            }
        }.getOrDefault(emptyList())
    }

    private fun writeList(list: List<VideoSource>) {
        prefs.edit().putString(KEY_LIST, gson.toJson(list)).apply()
    }
}
