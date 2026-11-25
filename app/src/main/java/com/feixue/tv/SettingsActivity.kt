package com.feixue.tv

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.feixue.tv.data.source.SourceManager
import com.feixue.tv.databinding.ActivitySettingsBinding
import com.feixue.tv.settings.AppSettings
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private var configValue: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.backButton.setOnClickListener { finish() }

        configValue = addRow(binding.configContainer, "配置地址", SourceManager.active().url) {
            startActivity(Intent(this, SourceActivity::class.java))
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
            recreate()
        }
        addRow(binding.leftColumn, "关于飞雪影视", "版本 ${currentVersion()}") {
            android.app.AlertDialog.Builder(this)
                .setTitle("关于飞雪影视")
                .setMessage("飞雪影视 ${currentVersion()}\n\n支持多数据源聚合搜索、最优线路选择、扫码搜索与多内核播放。\n仅供学习交流，请勿用于商业用途。")
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
        configValue?.text = active.url
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
}
