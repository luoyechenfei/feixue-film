package com.feixue.tv

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.feixue.tv.data.remote.ApiClient
import com.feixue.tv.data.source.SourceManager
import com.feixue.tv.data.source.VideoSource
import com.feixue.tv.databinding.ActivitySourcesBinding
import com.feixue.tv.databinding.DialogSourceBinding
import com.feixue.tv.databinding.DialogSubscriptionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

class SourceActivity : ComponentActivity() {
    private lateinit var binding: ActivitySourcesBinding
    private lateinit var adapter: SourceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySourcesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = SourceAdapter { source -> showActions(source) }
        binding.sourceList.apply {
            layoutManager = LinearLayoutManager(this@SourceActivity)
            adapter = this@SourceActivity.adapter
        }
        binding.backButton.setOnClickListener { finish() }
        binding.addButton.setOnClickListener { showEditDialog(null) }
        binding.subscribeButton.setOnClickListener { showSubscribeDialog() }
        binding.sourceState.setOnClickListener { refresh() }
        refresh()
        if (intent.getBooleanExtra(EXTRA_AUTO_ADD, false)) {
            showEditDialog(null)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val list = SourceManager.all()
        adapter.submit(list, SourceManager.activeId())
        binding.sourceState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.sourceState.text = "暂无数据源，点击新增"
    }

    private fun showActions(source: VideoSource) {
        val toggleLabel = if (source.enabled) "停用聚合" else "启用聚合"
        val actions = mutableListOf("设为当前数据源", "编辑", toggleLabel)
        if (SourceManager.all().size > 1) actions.add("删除")
        AlertDialog.Builder(this)
            .setTitle(source.name)
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    "设为当前数据源" -> {
                        SourceManager.setActive(source.id)
                        refresh()
                        Toast.makeText(this, "已切换到 ${source.name}", Toast.LENGTH_SHORT).show()
                    }
                    "编辑" -> showEditDialog(source)
                    toggleLabel -> {
                        SourceManager.upsert(source.copy(enabled = !source.enabled))
                        refresh()
                    }
                    "删除" -> confirmDelete(source)
                }
            }
            .show()
    }

    private fun confirmDelete(source: VideoSource) {
        AlertDialog.Builder(this)
            .setTitle("删除数据源")
            .setMessage("确定删除「${source.name}」吗？")
            .setPositiveButton("删除") { _, _ ->
                if (SourceManager.delete(source.id)) {
                    refresh()
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "至少需要保留一个数据源", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditDialog(existing: VideoSource?) {
        val dialogBinding = DialogSourceBinding.inflate(layoutInflater)
        dialogBinding.sourceNameInput.setText(existing?.name.orEmpty())
        dialogBinding.sourceUrlInput.setText(existing?.url.orEmpty())

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "新增数据源" else "编辑数据源")
            .setView(dialogBinding.root)
            .setPositiveButton("保存") { _, _ ->
                val name = dialogBinding.sourceNameInput.text.toString().trim()
                val url = SourceManager.normalizeUrl(dialogBinding.sourceUrlInput.text.toString())
                if (url.isBlank() || !url.startsWith("http")) {
                    Toast.makeText(this, "请输入有效的接口地址", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                SourceManager.upsert(
                    VideoSource(
                        id = existing?.id ?: SourceManager.createId(),
                        name = name.ifBlank { "第三方源" },
                        url = url,
                        builtin = existing?.builtin ?: false,
                    ),
                )
                refresh()
                Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSubscribeDialog() {
        val dialogBinding = DialogSubscriptionBinding.inflate(layoutInflater)
        AlertDialog.Builder(this)
            .setTitle("订阅导入")
            .setView(dialogBinding.root)
            .setPositiveButton("导入") { _, _ ->
                importSubscription(dialogBinding.subscriptionInput.text.toString().trim())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun importSubscription(text: String) {
        if (text.isEmpty()) {
            Toast.makeText(this, "请输入订阅地址或内容", Toast.LENGTH_SHORT).show()
            return
        }
        binding.sourceProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val content = if (text.startsWith("http", true)) {
                    withContext(Dispatchers.IO) { fetch(text) }
                } else {
                    text
                }
                val parsed = SourceManager.parseSubscription(content)
                if (parsed.isEmpty()) {
                    Toast.makeText(this@SourceActivity, "未解析到有效数据源", Toast.LENGTH_LONG).show()
                } else {
                    parsed.forEach { SourceManager.upsert(it) }
                    refresh()
                    Toast.makeText(this@SourceActivity, "成功导入 ${parsed.size} 个数据源", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                val reason = error.message ?: "请检查网络"
                Toast.makeText(this@SourceActivity, "订阅导入失败：$reason", Toast.LENGTH_LONG).show()
            } finally {
                binding.sourceProgress.visibility = View.GONE
            }
        }
    }

    private fun fetch(url: String): String {
        val request = Request.Builder().url(url).build()
        return ApiClient.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    companion object {
        const val EXTRA_AUTO_ADD = "auto_add"
    }
}
