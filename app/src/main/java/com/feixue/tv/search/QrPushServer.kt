package com.feixue.tv.search

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * 局域网扫码推送服务：手机访问 http://<电视IP>:<端口>/ 输入关键词后推送到电视。
 */
class QrPushServer(
    private val preferredPort: Int,
    private val onKeyword: (String) -> Unit,
) {
    private var serverSocket: ServerSocket? = null
    private var thread: Thread? = null

    var port: Int = 0
        private set

    var accessUrl: String = ""
        private set

    fun start(): Boolean {
        if (serverSocket != null) return true
        val socket = runCatching {
            var bound: ServerSocket? = null
            for (offset in 0..5) {
                bound = runCatching { ServerSocket(preferredPort + offset) }.getOrNull()
                if (bound != null) break
            }
            bound
        }.getOrNull() ?: return false
        serverSocket = socket
        port = socket.localPort
        accessUrl = "http://${localIpAddress()}:$port/"
        thread = Thread({ loop(socket) }, "qr-push-server").apply {
            isDaemon = true
            start()
        }
        return true
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        thread?.interrupt()
        thread = null
    }

    private fun loop(socket: ServerSocket) {
        while (!socket.isClosed) {
            try {
                socket.accept().use { handle(it) }
            } catch (error: Exception) {
                break
            }
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 8000
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        val parts = requestLine.split(' ')
        val method = parts.getOrNull(0) ?: "GET"
        val target = parts.getOrNull(1) ?: "/"

        var contentLength = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", true)) {
                contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
        }

        val body = if (method.equals("POST", true) && contentLength in 1..8192) {
            val chars = CharArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val result = reader.read(chars, read, contentLength - read)
                if (result <= 0) break
                read += result
            }
            String(chars, 0, read)
        } else {
            ""
        }

        val path = target.substringBefore('?')
        val query = if (target.contains('?')) target.substringAfter('?') else ""
        val params = parseParams(if (method.equals("POST", true)) "$query&$body" else query)
        val keyword = params["kw"] ?: params["keyword"] ?: params["wd"]

        val html = when {
            path == "/push" && !keyword.isNullOrBlank() -> {
                onKeyword(keyword)
                resultPage(keyword)
            }
            else -> formPage()
        }
        writeResponse(client, html)
    }

    private fun parseParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull { pair ->
            val index = pair.indexOf('=')
            if (index <= 0) return@mapNotNull null
            val key = pair.substring(0, index)
            val value = URLDecoder.decode(pair.substring(index + 1), "UTF-8")
            key to value
        }.toMap()
    }

    private fun writeResponse(client: Socket, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        runCatching {
            client.getOutputStream().apply {
                write(header.toByteArray(Charsets.US_ASCII))
                write(bytes)
                flush()
            }
        }
    }

    private fun formPage(): String = """
        <!doctype html>
        <html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>飞雪影视 · 扫码搜索</title>
        <style>
        body{margin:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#09111d;color:#f4f8fc;display:flex;min-height:100vh;align-items:center;justify-content:center}
        .card{width:100%;max-width:420px;padding:28px;box-sizing:border-box}
        h1{font-size:22px;margin:0 0 6px}
        p{color:#9aafc2;font-size:13px;margin:0 0 20px}
        input{width:100%;box-sizing:border-box;height:50px;border-radius:10px;border:1px solid #174e5b;background:#142233;color:#f4f8fc;font-size:17px;padding:0 14px;outline:none}
        button{width:100%;height:50px;margin-top:14px;border:0;border-radius:10px;background:#7ce8f2;color:#071018;font-size:17px;font-weight:600}
        </style></head>
        <body><div class="card">
        <h1>飞雪影视</h1>
        <p>输入片名、演员或导演，推送到电视搜索</p>
        <form method="post" action="/push">
        <input name="kw" placeholder="例如：两京十五日" autocomplete="off" autofocus>
        <button type="submit">推送到电视</button>
        </form>
        </div></body></html>
    """.trimIndent()

    private fun resultPage(keyword: String): String = """
        <!doctype html>
        <html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>已推送</title>
        <style>
        body{margin:0;font-family:-apple-system,sans-serif;background:#09111d;color:#f4f8fc;display:flex;min-height:100vh;align-items:center;justify-content:center;text-align:center}
        .card{padding:28px}
        h1{font-size:22px;margin:0 0 10px}
        p{color:#9aafc2;font-size:14px}
        a{display:inline-block;margin-top:18px;padding:12px 22px;border-radius:10px;background:#7ce8f2;color:#071018;text-decoration:none;font-weight:600}
        </style></head>
        <body><div class="card">
        <h1>已推送「${escape(keyword)}」</h1>
        <p>电视端已开始搜索，请查看电视画面。</p>
        <a href="/">继续搜索</a>
        </div></body></html>
    """.trimIndent()

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun localIpAddress(): String {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        }.getOrNull() ?: "127.0.0.1"
    }
}
