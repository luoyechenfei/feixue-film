package com.feixue.tv

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.feixue.tv.data.source.SourceManager
import com.feixue.tv.databinding.ActivitySettingsBinding
import com.feixue.tv.search.QrCodeUtil
import com.feixue.tv.search.QrPushServer
import com.feixue.tv.settings.AppSettings
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private var configValue: TextView? = null
    private var configPushServer: QrPushServer? = null
    private var configDialog: android.app.AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.backButton.setOnClickListener { finish() }

        configValue = addRow(binding.configContainer, "配置地址", configSummary()) {
            showConfigChooser()
        }

        addGroupTitle(binding.leftColumn, "首页")
        addOptionRow(
            binding.leftColumn, "推荐影片",
            listOf("true" to "开启", "false" to "关闭"),
            AppSettings.showRecommend.toString(),
        ) { AppSettings.showRecommend = it.toBoolean() }
        addOptionRow(
            binding.leftColumn, "搜索聚合",
            listOf("true" to "开启（多源合并）", "false" to "关闭（仅当前源）"),
            AppSettings.aggregateSearch.toString(),
        ) { AppSettings.aggregateSearch = it.toBoolean() }
        addRow(binding.leftColumn, "数据源管理", "新增 / 订阅 / 编辑") {
            startActivity(Intent(this, SourceActivity::class.java))
        }

        addGroupTitle(binding.leftColumn, "系统")
        addRow(binding.leftColumn, "检查更新", "当前 v${currentVersion()}") { checkForUpdate() }
        addRow(binding.leftColumn, "恢复默认设置", "重置") {
            AppSettings.engine = AppSettings.ENGINE_EXO
            AppSettings.decodeMode = AppSettings.DECODE_HARD
            AppSettings.speed = 1f
            AppSettings.introSeconds = 0
            AppSettings.outroSeconds = 0
            AppSettings.showRecommend = true
            AppSettings.aggregateSearch = true
            AppSettings.autoBestSource = true
            // 数据源一并恢复为仅内置默认地址
            SourceManager.resetToDefault()
            android.widget.Toast.makeText(this, "已恢复默认设置与默认地址", android.widget.Toast.LENGTH_SHORT).show()
            recreate()
        }
        addRow(binding.leftColumn, "关于飞雪影视", "版本 ${currentVersion()}") {
            android.app.AlertDialog.Builder(this)
                .setTitle("关于飞雪影视")
                .setMessage(
                    buildString {
                        append("飞雪影视 ${currentVersion()}\n\n")
                        append("支持多数据源聚合搜索、最优线路选择、扫码搜索与多内核播放。\n")
                        append("仅供学习交流，请勿用于商业用途。\n\n")
                        append("项目地址：$PROJECT_URL")
                    },
                )
                .setNeutralButton("项目地址") { _, _ -> openProjectPage() }
                .setPositiveButton("知道了", null)
                .show()
        }

        addGroupTitle(binding.rightColumn, "播放器")
        addOptionRow(
            binding.rightColumn, "默认播放内核",
            listOf(
                AppSettings.ENGINE_EXO to "ExoPlayer",
                AppSettings.ENGINE_IJK to "IJK 播放器",
                AppSettings.ENGINE_EXTERNAL to "外部播放器",
            ),
            AppSettings.engine,
        ) { AppSettings.engine = it }
        addOptionRow(
            binding.rightColumn, "默认解码方式",
            listOf(AppSettings.DECODE_HARD to "硬解码", AppSettings.DECODE_SOFT to "软解码"),
            AppSettings.decodeMode,
        ) { AppSettings.decodeMode = it }
        addOptionRow(
            binding.rightColumn, "默认播放速度",
            listOf("0.75" to "0.75x", "1.0" to "1.0x", "1.25" to "1.25x", "1.5" to "1.5x", "2.0" to "2.0x"),
            AppSettings.speed.toString(),
        ) { AppSettings.speed = it.toFloatOrNull() ?: 1f }
        addOptionRow(
            binding.rightColumn, "片头跳过",
            listOf("0" to "关闭", "5" to "5 秒", "10" to "10 秒", "15" to "15 秒", "20" to "20 秒", "30" to "30 秒"),
            AppSettings.introSeconds.toString(),
        ) { AppSettings.introSeconds = it.toIntOrNull() ?: 0 }
        addOptionRow(
            binding.rightColumn, "片尾跳过",
            listOf("0" to "关闭", "5" to "5 秒", "10" to "10 秒", "15" to "15 秒", "20" to "20 秒"),
            AppSettings.outroSeconds.toString(),
        ) { AppSettings.outroSeconds = it.toIntOrNull() ?: 0 }
        addOptionRow(
            binding.rightColumn, "自动最优线路",
            listOf("true" to "开启（测速选线）", "false" to "关闭（手动选线）"),
            AppSettings.autoBestSource.toString(),
        ) { AppSettings.autoBestSource = it.toBoolean() }
    }

    override fun onResume() {
        super.onResume()
        val active = SourceManager.active()
        binding.sourceSummary.text = "当前源：${active.name}"
        configValue?.text = configSummary()
    }

    private fun configSummary(): String {
        val count = SourceManager.all().size
        return "共 $count 个 · 当前 ${SourceManager.active().name}"
    }

    private fun showConfigChooser() {
        val items = arrayOf(
            "手机 / 电脑网页添加",
            "遥控器输入地址",
            "地址列表（编辑 / 删除 / 设为默认）",
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("配置地址")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showConfigQrDialog()
                    1 -> startActivity(
                        Intent(this, SourceActivity::class.java)
                            .putExtra(SourceActivity.EXTRA_AUTO_ADD, true),
                    )
                    else -> startActivity(Intent(this, SourceActivity::class.java))
                }
            }
            .show()
    }

    private fun showConfigQrDialog() {
        val server = QrPushServer(
            AppSettings.qrPushPort,
            pageTitle = "飞雪影视 · 添加配置地址",
            heading = "添加配置地址",
            hint = "手机扫码，或在电脑浏览器打开本页地址，填好后点击推送",
            placeholder = "接口地址或影视仓订阅链接（.json / .txt）",
            actionText = "推送到电视",
            resultHint = "电视端已添加该地址。",
        ) { pushed ->
            runOnUiThread { applyPushedConfig(pushed) }
        }
        if (!server.start()) {
            android.widget.Toast.makeText(this, "推送服务启动失败，请稍后重试", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        configPushServer = server

        val view = layoutInflater.inflate(R.layout.dialog_qr_push, null)
        view.findViewById<ImageView>(R.id.qrImage).setImageBitmap(QrCodeUtil.generate(server.accessUrl))
        view.findViewById<TextView>(R.id.qrUrlText).text = server.accessUrl
        view.findViewById<TextView>(R.id.qrHint).text =
            "手机扫码，或在电脑浏览器输入上面的地址；\n可填单个接口地址，也可填影视仓 / TVBox 订阅链接（.json / .txt）"

        configDialog = android.app.AlertDialog.Builder(this)
            .setTitle("手机 / 电脑添加配置地址")
            .setView(view)
            .setNegativeButton("关闭", null)
            .create()
            .apply {
                setOnDismissListener {
                    configPushServer?.stop()
                    configPushServer = null
                    configDialog = null
                }
                show()
            }
    }

    private fun applyPushedConfig(raw: String) {
        val value = raw.trim()
        if (value.isBlank()) return
        lifecycleScope.launch {
            val message = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                addAddress(value)
            }
            configValue?.text = configSummary()
            configDialog?.dismiss()
            android.widget.Toast.makeText(this@SettingsActivity, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** 订阅链接（含单仓/多仓）展开成多个源，普通链接按单个源添加 */
    private suspend fun addAddress(value: String): String {
        if (SourceManager.looksLikeConfigUrl(value)) {
            val result = com.feixue.tv.data.source.SourceImporter.fromUrl(value)
            if (result.sources.isNotEmpty()) {
                result.sources.forEach { SourceManager.upsert(it) }
                SourceManager.setActive(result.sources.first().id)
            }
            return result.message()
        }
        val url = SourceManager.normalizeUrl(value)
        if (!url.startsWith("http")) return "地址无效，未添加"
        val existing = SourceManager.all().firstOrNull { it.url == url }
        val source = existing ?: com.feixue.tv.data.source.VideoSource(
            id = SourceManager.createId(),
            name = android.net.Uri.parse(url).host?.removePrefix("www.") ?: "第三方源",
            url = url,
        )
        SourceManager.upsert(source)
        SourceManager.setActive(source.id)
        return "已添加 ${source.name}"
    }

    private fun openProjectPage() {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(PROJECT_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .onFailure {
                android.widget.Toast.makeText(this, PROJECT_URL, android.widget.Toast.LENGTH_LONG).show()
            }
    }

    private fun addGroupTitle(parent: LinearLayout, title: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(12), 0, dp(8))
        }
        val bar = android.view.View(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@SettingsActivity, R.color.accent))
        }
        container.addView(bar, LinearLayout.LayoutParams(dp(3), dp(14)))
        val text = TextView(this).apply {
            this.text = title
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_primary))
            setPadding(dp(8), 0, 0, 0)
        }
        container.addView(text)
        parent.addView(container)
    }

    private fun addOptionRow(
        parent: LinearLayout,
        label: String,
        options: List<Pair<String, String>>,
        currentValue: String,
        onPick: (String) -> Unit,
    ) {
        val initialText = options.firstOrNull { it.first == currentValue }?.second
            ?: options.first().second
        lateinit var rowRef: android.view.View
        val pair = createRow(parent, label, initialText) { valueView ->
            val current = rowRef.tag as? String ?: options.first().first
            val index = options.indexOfFirst { it.first == current }.coerceAtLeast(0)
            val next = options[(index + 1) % options.size]
            rowRef.tag = next.first
            valueView.text = next.second
            onPick(next.first)
        }
        rowRef = pair.first
        rowRef.tag = currentValue
    }

    private fun addRow(
        parent: LinearLayout,
        label: String,
        value: String,
        onClick: () -> Unit,
    ): TextView {
        val pair = createRow(parent, label, value) { _ -> onClick() }
        return pair.second
    }

    private fun createRow(
        parent: LinearLayout,
        label: String,
        value: String,
        onValueClick: (TextView) -> Unit,
    ): Pair<android.view.View, TextView> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_setting_row)
            setPadding(dp(14), 0, dp(14), 0)
            isFocusable = true
            isClickable = true
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_primary))
        }
        val valueView = TextView(this).apply {
            text = value
            textSize = 13f
            gravity = Gravity.END
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_secondary))
        }
        row.addView(
            labelView,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(
            valueView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        row.setOnClickListener { onValueClick(valueView) }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(46),
        ).apply { bottomMargin = dp(8) }
        parent.addView(row, params)
        return row to valueView
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun currentVersion(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull() ?: "1.0.0.1"

    private fun checkForUpdate() {
        android.widget.Toast.makeText(this, "正在检查更新…", android.widget.Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val release = com.feixue.tv.update.UpdateChecker.fetchLatest()
            if (release == null) {
                android.widget.Toast.makeText(
                    this@SettingsActivity, "检查更新失败，请检查网络后重试", android.widget.Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            val current = currentVersion()
            if (!com.feixue.tv.update.UpdateChecker.isNewer(release.version, current)) {
                android.app.AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("检查更新")
                    .setMessage("当前已是最新版本（v$current）")
                    .setPositiveButton("知道了", null)
                    .show()
                return@launch
            }
            android.app.AlertDialog.Builder(this@SettingsActivity)
                .setTitle("发现新版本 v${release.version}")
                .setMessage(buildString {
                    if (release.title.isNotBlank()) append(release.title).append("\n\n")
                    if (release.notes.isNotBlank()) append(release.notes).append("\n\n")
                    if (release.apkSize > 0) append("安装包大小：").append(formatSize(release.apkSize))
                    if (release.apkUrl.isBlank()) append("\n该版本未提供 APK 下载")
                })
                .setPositiveButton("立即更新") { _, _ ->
                    if (release.apkUrl.isNotBlank()) startDownload(release)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private var downloadCancelled = false

    private fun startDownload(release: com.feixue.tv.update.UpdateChecker.ReleaseInfo) {
        val view = layoutInflater.inflate(R.layout.view_update_progress, null)
        val progressBar = view.findViewById<android.widget.ProgressBar>(R.id.downloadProgress)
        val percentText = view.findViewById<TextView>(R.id.downloadPercent)
        val speedText = view.findViewById<TextView>(R.id.downloadSpeed)
        downloadCancelled = false
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("正在下载 v${release.version}")
            .setView(view)
            .setCancelable(false)
            .setNegativeButton("取消下载") { _, _ -> downloadCancelled = true }
            .create()
        dialog.show()

        val target = java.io.File(getExternalFilesDir("updates"), "feixue-film-v${release.version}.apk")
        lifecycleScope.launch {
            val ok = com.feixue.tv.update.UpdateChecker.download(
                release.apkUrl,
                target,
                isCancelled = { downloadCancelled },
            ) { downloaded, total, speed ->
                runOnUiThread {
                    if (total > 0) {
                        val percent = (downloaded * 100 / total).toInt().coerceIn(0, 100)
                        progressBar.progress = percent
                        percentText.text = "$percent%（${formatSize(downloaded)} / ${formatSize(total)}）"
                    } else {
                        percentText.text = "已下载 ${formatSize(downloaded)}"
                    }
                    speedText.text = if (speed > 0) "下载速度：${formatSize(speed)}/s" else "即将完成…"
                }
            }
            dialog.dismiss()
            when {
                ok -> installApk(target)
                downloadCancelled -> android.widget.Toast.makeText(
                    this@SettingsActivity, "已取消下载", android.widget.Toast.LENGTH_SHORT,
                ).show()
                else -> android.widget.Toast.makeText(
                    this@SettingsActivity, "下载失败，请稍后重试", android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun installApk(file: java.io.File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                android.widget.Toast.makeText(this, "无法打开安装器", android.widget.Toast.LENGTH_SHORT).show()
            }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1024f / 1024f)
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024f)
        else -> "$bytes B"
    }

    private companion object {
        const val PROJECT_URL = "https://github.com/luoyechenfei/feixue-film"
    }
}
