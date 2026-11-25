package com.feixue.tv

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.feixue.tv.data.repository.VodRepository
import com.feixue.tv.data.source.SourceManager
import com.feixue.tv.databinding.ActivitySearchBinding
import com.feixue.tv.search.QrCodeUtil
import com.feixue.tv.search.QrPushServer
import com.feixue.tv.settings.AppSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class SearchActivity : ComponentActivity() {
    private lateinit var binding: ActivitySearchBinding
    private val repository = VodRepository()
    private lateinit var adapter: VodAdapter
    private var pushServer: QrPushServer? = null

    private var keyword = ""
    private var nextPage = 1
    private var loading = false
    private var endReached = false
    private var requestVersion = 0

    private lateinit var sourceFilterAdapter: TextOptionAdapter
    private var allResults: List<com.feixue.tv.data.model.VodDto> = emptyList()
    private var activeFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = VodAdapter { vod -> DetailActivity.start(this, vod.vodId, vod.sourceId) }
        binding.resultList.apply {
            layoutManager = GridLayoutManager(this@SearchActivity, 6)
            adapter = this@SearchActivity.adapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0 || loading || endReached || keyword.isEmpty()) return
                    val grid = recyclerView.layoutManager as GridLayoutManager
                    if (grid.findLastVisibleItemPosition() >= this@SearchActivity.adapter.itemCount - 6) {
                        loadResults(false)
                    }
                }
            })
        }

        binding.backButton.setOnClickListener { finish() }
        sourceFilterAdapter = TextOptionAdapter { id, title ->
            activeFilter = if (id == 0) null else title
            applyFilter()
        }
        binding.sourceFilterList.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@SearchActivity)
            adapter = sourceFilterAdapter
        }
        binding.searchButton.setOnClickListener { startSearch() }
        binding.qrButton.setOnClickListener { toggleQrPanel() }
        binding.qrCloseButton.setOnClickListener { hideQrPanel() }
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                startSearch()
                true
            } else {
                false
            }
        }
        binding.searchInput.requestFocus()

        val preset = intent.getStringExtra(EXTRA_KEYWORD).orEmpty()
        if (preset.isNotBlank()) {
            binding.searchInput.setText(preset)
            startSearch()
        }
    }

    private fun toggleQrPanel() {
        if (binding.qrPanel.visibility == View.VISIBLE) {
            hideQrPanel()
            return
        }
        val server = QrPushServer(AppSettings.qrPushPort) { pushed ->
            runOnUiThread { applyPushedKeyword(pushed) }
        }
        if (server.start()) {
            pushServer = server
            binding.qrUrlText.text = server.accessUrl
            binding.qrImage.setImageBitmap(QrCodeUtil.generate(server.accessUrl))
        } else {
            binding.qrUrlText.text = "推送服务启动失败，请稍后重试"
            binding.qrImage.setImageBitmap(null)
        }
        binding.qrPanel.visibility = View.VISIBLE
        binding.qrCloseButton.requestFocus()
    }

    private fun hideQrPanel() {
        binding.qrPanel.visibility = View.GONE
        pushServer?.stop()
        pushServer = null
    }

    private fun applyPushedKeyword(value: String) {
        val text = value.trim()
        if (text.isEmpty()) return
        hideQrPanel()
        binding.searchInput.setText(text)
        startSearch()
    }

    private fun startSearch() {
        val value = binding.searchInput.text.toString().trim()
        if (value.isEmpty()) {
            binding.stateText.text = "请输入搜索关键词"
            binding.stateText.visibility = View.VISIBLE
            return
        }
        val inputManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
        keyword = value
        binding.resultTitle.text = "“$value” 的搜索结果"
        loadResults(true)
    }

    private fun loadResults(reset: Boolean) {
        if (AppSettings.aggregateSearch) {
            loadAggregate(reset)
            return
        }
        if (loading || (!reset && endReached)) return
        if (reset) {
            requestVersion++
            nextPage = 1
            endReached = false
            allResults = emptyList()
            activeFilter = null
            binding.sourceFilterList.visibility = View.GONE
            adapter.setItems(emptyList())
            binding.resultList.scrollToPosition(0)
        }

        val version = requestVersion
        val requestedPage = nextPage
        loading = true
        binding.progressBar.visibility = View.VISIBLE
        binding.stateText.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val items = repository.search(keyword, requestedPage)
                if (version != requestVersion) return@launch
                if (reset) adapter.setItems(items) else adapter.appendItems(items)
                endReached = items.size < 20
                nextPage = requestedPage + 1
                binding.resultTitle.text = "“$keyword” · 已加载 ${adapter.itemCount} 部"
                if (adapter.itemCount == 0) {
                    binding.stateText.text = "没有找到相关影片，请换个关键词"
                    binding.stateText.visibility = View.VISIBLE
                }
            } catch (error: Exception) {
                if (version != requestVersion) return@launch
                if (reset || adapter.itemCount == 0) {
                    binding.stateText.text = "搜索失败，请检查网络或数据源后重试"
                    binding.stateText.visibility = View.VISIBLE
                } else {
                    Toast.makeText(this@SearchActivity, "下一页加载失败", Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (version == requestVersion) {
                    loading = false
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun loadAggregate(reset: Boolean) {
        if (loading || !reset) return
        requestVersion++
        val version = requestVersion
        loading = true
        binding.sourceFilterList.visibility = View.GONE
        allResults = emptyList()
        adapter.setItems(emptyList())
        binding.resultList.scrollToPosition(0)
        binding.progressBar.visibility = View.VISIBLE
        binding.stateText.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val enabled = SourceManager.enabledSources().ifEmpty {
                    listOf(SourceManager.active())
                }
                val results = coroutineScope {
                    enabled.map { source ->
                        async {
                            runCatching {
                                VodRepository(source).search(keyword, 1).onEach {
                                    it.sourceId = source.id
                                    it.sourceName = source.name
                                }
                            }.getOrDefault(emptyList())
                        }
                    }.awaitAll()
                }
                if (version != requestVersion) return@launch
                val merged = interleave(results)
                allResults = merged
                activeFilter = null
                val names = merged.map { it.sourceName }.distinct().filter { it.isNotBlank() }
                sourceFilterAdapter.setItems(
                    listOf(0 to "全部显示") + names.mapIndexed { i, name -> i + 1 to name },
                    0,
                )
                binding.sourceFilterList.visibility = if (names.size > 1) View.VISIBLE else View.GONE
                adapter.setItems(merged)
                endReached = true
                binding.resultTitle.text = aggregateTitle()
                if (merged.isEmpty()) {
                    binding.stateText.text = "各数据源均未找到相关影片"
                    binding.stateText.visibility = View.VISIBLE
                }
            } catch (error: Exception) {
                if (version != requestVersion) return@launch
                binding.stateText.text = "聚合搜索失败，请检查网络后重试"
                binding.stateText.visibility = View.VISIBLE
            } finally {
                if (version == requestVersion) {
                    loading = false
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun applyFilter() {
        val filtered = activeFilter?.let { name ->
            allResults.filter { it.sourceName == name }
        } ?: allResults
        adapter.setItems(filtered)
        binding.resultTitle.text = aggregateTitle()
    }

    private fun aggregateTitle(): String =
        "搜索（${adapter.itemCount}/${allResults.size}）· ${SourceManager.enabledSources().size} 个源"

    private fun interleave(lists: List<List<com.feixue.tv.data.model.VodDto>>): List<com.feixue.tv.data.model.VodDto> {
        val result = mutableListOf<com.feixue.tv.data.model.VodDto>()
        val maxSize = lists.maxOfOrNull { it.size } ?: 0
        for (index in 0 until maxSize) {
            for (list in lists) {
                if (index < list.size) result.add(list[index])
            }
        }
        return result
    }

    override fun onDestroy() {
        pushServer?.stop()
        pushServer = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_KEYWORD = "keyword"

        fun start(context: android.content.Context, keyword: String) {
            context.startActivity(
                android.content.Intent(context, SearchActivity::class.java).apply {
                    putExtra(EXTRA_KEYWORD, keyword)
                },
            )
        }
    }
}
