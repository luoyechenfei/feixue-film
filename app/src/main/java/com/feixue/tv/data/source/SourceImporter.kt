package com.feixue.tv.data.source

import com.feixue.tv.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request

data class ImportResult(
    val sources: List<VideoSource>,
    val spiderOnly: Boolean = false,
    val error: String? = null,
) {
    fun message(): String = when {
        sources.isNotEmpty() -> "已导入 ${sources.size} 个地址"
        spiderOnly -> "该配置只包含 TVBox 爬虫源（csp_ 开头），本应用无法运行爬虫，请改用 CMS 接口地址"
        error != null -> "导入失败：$error"
        else -> "未解析到可用地址"
    }
}

/**
 * 订阅导入：支持单仓（sites）、多仓（urls/configs 指向子仓），递归最多两层。
 * 只保留能直接访问的 CMS 接口，蜘蛛源跳过。
 */
object SourceImporter {
    private const val MAX_DEPTH = 2
    private const val MAX_SUB_CONFIGS = 20

    suspend fun fromUrl(url: String): ImportResult {
        val content = runCatching { fetch(url) }
            .getOrElse {
                android.util.Log.w("SourceImporter", "读取订阅失败 $url", it)
                return ImportResult(emptyList(), error = it.message ?: "网络错误")
            }
        return fromContent(content)
    }

    suspend fun fromContent(content: String): ImportResult = collect(content, 0)

    private suspend fun collect(content: String, depth: Int): ImportResult {
        val direct = SourceManager.parseSubscription(content)
        if (direct.isNotEmpty()) return ImportResult(direct)
        if (depth >= MAX_DEPTH) return ImportResult(emptyList(), spiderOnly = content.contains("csp_"))

        // 导航页（HTML）里通常会列出若干配置链接，先抓出来再逐个解析
        val subConfigs = if (content.trimStart().startsWith("<")) {
            extractConfigLinks(content)
        } else {
            SourceManager.parseMultiConfigUrls(content).take(MAX_SUB_CONFIGS)
        }
        if (subConfigs.isEmpty()) return ImportResult(emptyList(), spiderOnly = content.contains("csp_"))

        val permits = Semaphore(4)
        val mutex = Mutex()
        val collected = mutableListOf<VideoSource>()
        var failedChildren = 0
        coroutineScope {
            subConfigs.forEach { (label, url) ->
                launch(Dispatchers.IO) {
                    permits.withPermit {
                        val body = runCatching { fetch(url) }.getOrNull()
                        if (body == null) {
                            mutex.withLock { failedChildren++ }
                            return@withPermit
                        }
                        val child = collect(body, depth + 1)
                        val named = child.sources.map { source ->
                            if (source.name.isBlank() || source.name == "第三方源") {
                                source.copy(name = label)
                            } else {
                                source
                            }
                        }
                        mutex.withLock { collected.addAll(named) }
                    }
                }
            }
        }

        val sources = collected.distinctBy { it.url }
        if (sources.isEmpty()) {
            android.util.Log.w("SourceImporter", "子仓 ${subConfigs.size} 个，失败 $failedChildren 个")
        }
        return when {
            sources.isNotEmpty() -> ImportResult(sources)
            failedChildren > 0 -> ImportResult(emptyList(), error = "$failedChildren 个子仓无法访问")
            else -> ImportResult(emptyList(), spiderOnly = true)
        }
    }

    private val configLinkRegex =
        Regex("""https?://[^\s"'<>()\\]+\.(?:json|txt|yaml)""", RegexOption.IGNORE_CASE)

    private fun extractConfigLinks(html: String): List<Pair<String, String>> =
        configLinkRegex.findAll(html)
            .map { "子仓" to it.value }
            .distinctBy { it.second }
            .take(6)
            .toList()

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0")
            .build()
        return ApiClient.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }
}
