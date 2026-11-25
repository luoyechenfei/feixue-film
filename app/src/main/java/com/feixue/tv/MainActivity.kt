package com.feixue.tv

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.feixue.tv.data.model.VodDto
import com.feixue.tv.data.repository.VodRepository
import com.feixue.tv.data.source.SourceManager
import com.feixue.tv.databinding.ActivityMainBinding
import com.feixue.tv.settings.AppSettings
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private val repository = VodRepository()
    private lateinit var vodAdapter: VodAdapter
    private lateinit var recommendAdapter: RecommendAdapter
    private lateinit var categoryAdapter: TextOptionAdapter

    private val yearOptions = listOf(
        "全部", "2026", "2025", "2024", "2023", "2022", "2021",
        "2020", "2019", "2018", "2017", "2016", "2015",
    )
    private val areaOptions = listOf("全部", "大陆", "香港", "台湾", "美国", "韩国", "日本", "其他")
    private val sortOptions = listOf("最近更新", "评分优先", "播放量优先")

    private var yearIndex = 0
    private var areaIndex = 0
    private var sortIndex = 0

    private var currentType = 0
    private var currentTitle = "最近更新"
    private var nextPage = 1
    private var loading = false
    private var endReached = false
    private var requestVersion = 0
    private var recommendLoaded = false
    private var lastSourceId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vodAdapter = VodAdapter { vod -> DetailActivity.start(this, vod.vodId) }
        binding.vodList.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 6)
            adapter = vodAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0 || loading || endReached) return
                    val grid = recyclerView.layoutManager as GridLayoutManager
                    if (grid.findLastVisibleItemPosition() >= vodAdapter.itemCount - 6) {
                        loadPage(false)
                    }
                }
            })
        }

        recommendAdapter = RecommendAdapter { vod -> DetailActivity.start(this, vod.vodId) }
        binding.recommendList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
            adapter = recommendAdapter
        }

        categoryAdapter = TextOptionAdapter { id, title ->
            when (id) {
                -2 -> startActivity(Intent(this, HistoryActivity::class.java))
                -3 -> startActivity(Intent(this, FavoritesActivity::class.java))
                else -> {
                    currentType = id
                    currentTitle = if (id == 0) "最近更新" else title
                    updateSectionVisibility()
                    loadPage(true)
                }
            }
        }
        binding.categoryList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
            adapter = categoryAdapter
        }
        categoryAdapter.setItems(homeMenuItems(), 0)

        binding.searchButton.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.stateText.setOnClickListener { loadPage(true) }

        binding.yearFilter.setOnClickListener {
            yearIndex = (yearIndex + 1) % yearOptions.size
            updateFilterLabels()
            loadPage(true)
        }
        binding.areaFilter.setOnClickListener {
            areaIndex = (areaIndex + 1) % areaOptions.size
            updateFilterLabels()
            loadPage(true)
        }
        binding.sortFilter.setOnClickListener {
            sortIndex = (sortIndex + 1) % sortOptions.size
            updateFilterLabels()
            loadPage(true)
        }
        updateFilterLabels()

        lastSourceId = SourceManager.activeId()
        loadRecommend()
        loadCategories()
        loadPage(true)
    }

    override fun onResume() {
        super.onResume()
        if (::categoryAdapter.isInitialized) categoryAdapter.setSelectedById(currentType)
        if (lastSourceId != SourceManager.activeId()) {
            lastSourceId = SourceManager.activeId()
            currentType = 0
            yearIndex = 0
            areaIndex = 0
            sortIndex = 0
            updateFilterLabels()
            updateSectionVisibility()
            recommendLoaded = false
            recommendAdapter.setItems(emptyList())
            loadRecommend()
            loadCategories()
            loadPage(true)
        }
    }

    private fun updateFilterLabels() {
        binding.yearFilter.text = "年份：${yearOptions[yearIndex]}"
        binding.areaFilter.text = "地区：${areaOptions[areaIndex]}"
        binding.sortFilter.text = "排序：${sortOptions[sortIndex]}"
    }

    private fun updateSectionVisibility() {
        val home = currentType == 0
        binding.recommendSection.visibility =
            if (home && AppSettings.showRecommend) View.VISIBLE else View.GONE
        binding.filterBar.visibility = if (home) View.GONE else View.VISIBLE
    }

    private fun loadRecommend() {
        if (recommendLoaded || !AppSettings.showRecommend) return
        recommendLoaded = true
        lifecycleScope.launch {
            runCatching { repository.recommend(12) }
                .onSuccess { items ->
                    recommendAdapter.setItems(items)
                    if (items.isEmpty()) recommendLoaded = false
                }
                .onFailure { recommendLoaded = false }
        }
    }

    private fun homeMenuItems(): List<Pair<Int, String>> =
        listOf(0 to "首页", -2 to "历史", -3 to "收藏")

    private fun loadCategories() {
        lifecycleScope.launch {
            runCatching { repository.categories() }
                .onSuccess { categories ->
                    val leaves = categories.filter { it.typePid > 0 }
                    val usable = if (leaves.isNotEmpty()) leaves else categories
                    categoryAdapter.setItems(
                        homeMenuItems() + usable.map { it.typeId to it.typeName },
                        0,
                    )
                }
                .onFailure {
                    Toast.makeText(this@MainActivity, "分类加载失败，仍可浏览首页", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadPage(reset: Boolean) {
        if (loading || (!reset && endReached)) return
        if (reset) {
            requestVersion++
            nextPage = 1
            endReached = false
            vodAdapter.setItems(emptyList())
            binding.vodList.scrollToPosition(0)
        }

        val version = requestVersion
        var requestedPage = nextPage
        loading = true
        binding.progressBar.visibility = View.VISIBLE
        binding.stateText.visibility = View.GONE
        binding.sectionTitle.text = currentTitle

        lifecycleScope.launch {
            try {
                val collected = mutableListOf<VodDto>()
                var lastRawCount = 0
                var batches = 0
                val maxBatches = if (areaIndex > 0) 3 else 1
                while (collected.size < 20 && batches < maxBatches) {
                    val raw = if (currentType == 0) {
                        repository.home(requestedPage)
                    } else {
                        repository.loadCategory(
                            typeId = currentType,
                            page = requestedPage,
                            year = if (yearIndex == 0) null else yearOptions[yearIndex],
                        )
                    }
                    lastRawCount = raw.size
                    collected.addAll(applyFilters(raw))
                    requestedPage++
                    batches++
                    if (raw.size < 20) break
                }
                if (version != requestVersion) return@launch

                if (reset) vodAdapter.setItems(collected) else vodAdapter.appendItems(collected)
                endReached = lastRawCount < 20
                nextPage = requestedPage
                binding.pageText.text = "第 ${requestedPage - 1} 页 · 已加载 ${vodAdapter.itemCount} 部"
                if (vodAdapter.itemCount == 0) {
                    showState(if (hasActiveFilter()) "当前筛选暂无影片，点击重试" else "暂无影片，点击重试")
                }
            } catch (error: Exception) {
                if (version != requestVersion) return@launch
                if (reset || vodAdapter.itemCount == 0) {
                    showState("加载失败，请检查网络或数据源后点击重试")
                } else {
                    Toast.makeText(this@MainActivity, "下一页加载失败，请稍后重试", Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (version == requestVersion) {
                    loading = false
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun hasActiveFilter(): Boolean =
        currentType != 0 && (yearIndex > 0 || areaIndex > 0 || sortIndex > 0)

    private fun applyFilters(items: List<VodDto>): List<VodDto> {
        var result = items
        val area = areaOptions[areaIndex]
        if (area != "全部") {
            result = result.filter { matchArea(it.vodArea, area) }
        }
        result = when (sortIndex) {
            1 -> result.sortedByDescending { it.scoreValue }
            2 -> result.sortedByDescending { it.hitsValue }
            else -> result
        }
        return result
    }

    private fun matchArea(area: String?, target: String): Boolean {
        val value = area.orEmpty()
        if (value.isBlank()) return false
        val known = listOf(
            "中国", "大陆", "香港", "台湾", "美国", "韩国", "日本",
            "泰国", "印度", "英国", "法国", "德国", "其他",
        )
        return when (target) {
            "大陆" -> value.contains("中国") || value.contains("大陆")
            "香港" -> value.contains("香港")
            "台湾" -> value.contains("台湾")
            "美国" -> value.contains("美国")
            "韩国" -> value.contains("韩国")
            "日本" -> value.contains("日本")
            "其他" -> known.none { value.contains(it) }
            else -> value.contains(target)
        }
    }

    private fun showState(message: String) {
        binding.stateText.text = message
        binding.stateText.visibility = View.VISIBLE
    }
}
