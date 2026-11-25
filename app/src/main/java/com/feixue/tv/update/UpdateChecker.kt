package com.feixue.tv.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/** GitHub Release 更新检测与 APK 下载 */
object UpdateChecker {
    const val OWNER = "luoyechenfei"
    const val REPO = "feixue-film"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    data class ReleaseInfo(
        val version: String,
        val title: String,
        val notes: String,
        val apkUrl: String,
        val apkSize: Long,
    )

    suspend fun fetchLatest(): ReleaseInfo? {
        repeat(3) { attempt ->
            val result = fetchLatestOnce()
            if (result != null) return result
            if (attempt < 2) kotlinx.coroutines.delay(1500L)
        }
        return null
    }

    private suspend fun fetchLatestOnce(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "FeixueTV-Android")
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string().orEmpty())
                var apkUrl = ""
                var apkSize = 0L
                val assets = json.optJSONArray("assets")
                for (index in 0 until (assets?.length() ?: 0)) {
                    val asset = assets!!.getJSONObject(index)
                    if (asset.optString("name").endsWith(".apk", true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkSize = asset.optLong("size")
                        break
                    }
                }
                ReleaseInfo(
                    version = json.optString("tag_name").removePrefix("v"),
                    title = json.optString("name"),
                    notes = json.optString("body"),
                    apkUrl = apkUrl,
                    apkSize = apkSize,
                )
            }
        }.getOrNull()
    }

    /** 语义化版本比较：latest 高于 current 时返回 true */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(index) { 0 }
            val y = b.getOrElse(index) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** 下载通道：GitHub 直连失败时自动切换加速代理 */
    private fun candidateUrls(url: String): List<String> = listOf(
        url,
        "https://gh-proxy.com/$url",
        "https://ghfast.top/$url",
    )

    /** 流式下载，回调已下载字节、总字节与实时速度（字节/秒），支持取消与多通道重试 */
    suspend fun download(
        url: String,
        target: File,
        isCancelled: () -> Boolean = { false },
        onProgress: (downloaded: Long, total: Long, speedBps: Long) -> Unit,
    ): Boolean {
        for (candidate in candidateUrls(url)) {
            if (isCancelled()) return false
            if (downloadOnce(candidate, target, isCancelled, onProgress)) return true
        }
        target.delete()
        return false
    }

    private suspend fun downloadOnce(
        url: String,
        target: File,
        isCancelled: () -> Boolean,
        onProgress: (downloaded: Long, total: Long, speedBps: Long) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).header("User-Agent", "FeixueTV-Android").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val total = body.contentLength()
                target.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var lastTime = System.nanoTime()
                        var lastBytes = 0L
                        while (true) {
                            if (isCancelled()) {
                                output.close()
                                target.delete()
                                return@withContext false
                            }
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.nanoTime()
                            val elapsed = now - lastTime
                            if (elapsed >= 300_000_000L) {
                                val speed = ((downloaded - lastBytes) * 1_000_000_000L / elapsed)
                                onProgress(downloaded, total, speed)
                                lastTime = now
                                lastBytes = downloaded
                            }
                        }
                        onProgress(downloaded, total, 0L)
                    }
                }
                downloadedSizeValid(target)
            }
        }.getOrDefault(false)
    }

    private fun downloadedSizeValid(target: File): Boolean = target.length() > 1024 * 1024
}
