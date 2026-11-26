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
import com.feixue.tv.data.model.CategoryDto
import com.feixue.tv.data.model.VodDto
import com.feixue.tv.data.repository.DoubanRepository
import com.feixue.tv.data.repository.VodRepository
import com.feixue.tv.data.source.SourceManager
import com.feixue.tv.databinding.ActivityMainBinding
import com.feixue.tv.settings.AppSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private val repository = VodRepository()
    private val doubanRepository by lazy { DoubanRepository(applicationContext) }
    private lateinit var vodAdapter: VodAdapter
    private lateinit var recommendAdapter: RecommendAdapter
    private lateinit var categoryAdapter: TextOptionAdapter
    private lateinit var subCategoryAdapter: TextOptionAdapter

    private val yearOptions = listOf(
        "全部", "2026", "2025", "2024", "2023", "2022", "2021",
        "2020", "2019", "2018", "2017", "2016", "2015",
    )
    private val areaOptions = listOf("全部", "大陆", "香港", "台湾", "美国", "韩国", "日本", "其他")
    private val sortOptions = listOf("最近更新", "评分优先", "播放量优先")

    private val sectionOrder = listOf(
        "电影" to listOf("电影"),
        "电视剧" to listOf("连续剧", "电视剧", "剧集"),
        "综艺" to listOf("综艺"),
        "动漫" to listOf("动漫", "动画"),
    )

    private val OTHER_SECTION_ID = -4
    private val RECOMMEND_CACHE_TTL = 6 * 60 * 60 * 1000L

    private var categories: List<CategoryDto> = emptyList()
    private var parentSections: List<Pair<Int, String>> = emptyList()

    private var yearIndex = 0
    private var areaIndex = 0
    private var sortIndex = 0

    private var currentType = 0
    private var currentParentId = 0
    private var currentTitle = "最近更新"
    private var nextPage = 1
    private var loading = false
    private var endReached = false
    private var requestVersion = 0
    private var recommendLoaded = false
    private val recommendIds = mutableMapOf<String, Int>()
    private var lastSourceId = ""
    private var lastSourceUrl = ""

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

        recommendAdapter = RecommendAdapter { vod ->
            if (vod.vodId > 0) {
                DetailActivity.start(this, vod.vodId)
            } else {
                openRecommendByTitle(vod.vodName.orEmpty())
            }
        }
        binding.recommendList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
            adapter = recommendAdapter
        }

        categoryAdapter = TextOptionAdapter { id, title ->
            when (id) {
                -2 -> startActivity(Intent(this, HistoryActivity::class.java))
                -3 -> startActivity(Intent(this, FavoritesActivity::class.java))
                0 -> {
                    currentParentId = 0
                    currentType = 0
                    currentTitle = "最近更新"
                    binding.subCategoryList.visibility = View.GONE
                    updateSectionVisibility()
                    loadPage(true)
                }
                else -> {
                    if (parentSections.any { it.first == id }) {
                        currentParentId = id
                        currentType = id
                        currentTitle = title
                        subCategoryAdapter.setItems(subMenuItems(id), 0)
                        binding.subCategoryList.visibility = View.VISIBLE
                    } else {
                        currentParentId = 0
                        currentType = id
                        currentTitle = title
                        binding.subCategoryList.visibility = View.GONE
                    }
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

        subCategoryAdapter = TextOptionAdapter { id, title ->
            currentType = id
            currentTitle = if (id == currentParentId) parentTitle(currentParentId) else title
            loadPage(true)
        }
        binding.subCategoryList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
            adapter = subCategoryAdapter
        }

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
        lastSourceUrl = SourceManager.active().url
        loadRecommend()
        loadCategories()
        loadPage(true)
    }

    override fun onResume() {
        super.onResume()
        if (::categoryAdapter.isInitialized) {
            categoryAdapter.setSelectedById(
                when {
                    currentType == 0 -> 0
                    currentParentId != 0 -> currentParentId
                    else -> currentType
                },
            )
        }
        if (::subCategoryAdapter.isInitialized && currentParentId != 0) {
            subCategoryAdapter.setSelectedById(currentType)
        }
        if (lastSourceId != SourceManager.activeId() || lastSourceUrl != SourceManager.active().url) {
            lastSourceId = SourceManager.activeId()
            lastSourceUrl = SourceManager.active().url
            currentType = 0
            currentParentId = 0
            yearIndex = 0
            areaIndex = 0
            sortIndex = 0
            updateFilterLabels()
            updateSectionVisibility()
            recommendLoaded = false
            recommendIds.clear()
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
        binding.subCategoryList.visibility = if (currentParentId == 0) View.GONE else View.VISIBLE
    }

    private fun loadRecommend() {
        if (!AppSettings.showRecommend) return
        if (recommendLoaded) return

        val cached = doubanRepository.cachedRecommend()
        if (cached.isNotEmpty()) {
            // 有缓存立即上屏，不再空等
            recommendLoaded = true
            recommendAdapter.setItems(cached)
            enrichRecommend(cached)
            if (doubanRepository.cacheAge() < RECOMMEND_CACHE_TTL) return
        }

        lifecycleScope.launch {
            val items = runCatching { doubanRepository.hot(12) }.getOrDefault(emptyList())
                .ifEmpty { runCatching { repository.recommend(12) }.getOrDefault(emptyList()) }
            if (items.isEmpty()) {
                if (cached.isEmpty()) recommendLoaded = false
                return@launch
            }
            recommendLoaded = true
            recommendAdapter.setItems(items)
            enrichRecommend(items)
        }
    }

    /** 后台把豆瓣片名解析成真实片源 id，点击时就可以直接进详情 */
    private fun enrichRecommend(items: List<VodDto>) {
        val pending = items.filter { it.vodId == 0 && !it.vodName.isNullOrBlank() }
        if (pending.isEmpty()) return
        lifecycleScope.launch {
            val permits = Semaphore(3)
            pending.forEach { item ->
                launch {
                    permits.withPermit {
                        val title = item.vodName.orEmpty()
                        if (!recommendIds.containsKey(title)) {
                            doubanRepository.resolve(title)?.let { recommendIds[title] = it }
                        }
                    }
                }
            }
        }
    }

    private fun openRecommendByTitle(title: String) {
        if (title.isBlank()) return
        recommendIds[title]?.let {
            DetailActivity.start(this, it)
            return
        }
        Toast.makeText(this, "正在查找「$title」的片源…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val vodId = doubanRepository.resolve(title)
            if (vodId == null) {
                Toast.makeText(this@MainActivity, "「$title」暂无可播放片源", Toast.LENGTH_SHORT).show()
            } else {
                recommendIds[title] = vodId
                DetailActivity.start(this@MainActivity, vodId)
            }
        }
    }

    private fun homeMenuItems(): List<Pair<Int, String>> =
        listOf(0 to "首页", -2 to "历史", -3 to "收藏") + parentSections

    private fun parentTitle(parentId: Int): String =
        parentSections.firstOrNull { it.first == parentId }?.second.orEmpty()

    private fun subMenuItems(parentId: Int): List<Pair<Int, String>> {
        val leaves = if (parentId == OTHER_SECTION_ID) {
            otherLeaves(categories)
        } else {
            categories.filter { it.typePid == parentId }
        }
        return listOf(parentId to "全部") + leaves.map { it.typeId to it.typeName }
    }

    private fun leafIdsOf(parentId: Int): List<Int> =
        if (parentId == OTHER_SECTION_ID) {
            otherLeaves(categories).map { it.typeId }
        } else {
            categories.filter { it.typePid == parentId }.map { it.typeId }
        }

    /** 不在电影、电视剧、综艺、动漫下的所有子分类 */
    private fun otherLeaves(list: List<CategoryDto>): List<CategoryDto> {
        val sectionParentIds = sectionOrder.mapNotNull { (_, keys) ->
            list.firstOrNull { it.typePid == 0 && keys.any { key -> it.typeName.contains(key) } }?.typeId
        }.toSet()
        return list.filter { it.typePid > 0 && it.typePid !in sectionParentIds }
    }

    private fun resolveParentSections(list: List<CategoryDto>): List<Pair<Int, String>> {
        val parents = list.filter { it.typePid == 0 }
        val sections = sectionOrder.mapNotNull { (display, keys) ->
            parents.firstOrNull { parent -> keys.any { parent.typeName.contains(it) } }
                ?.let { it.typeId to display }
        }
        return if (otherLeaves(list).isNotEmpty()) sections + (OTHER_SECTION_ID to "其他") else sections
    }

    private fun loadCategories() {
        lifecycleScope.launch {
            runCatching { repository.categories() }
                .onSuccess { list ->
                    categories = list
                    if (SourceManager.active().builtin) {
                        parentSections = resolveParentSections(list)
                        categoryAdapter.setItems(homeMenuItems(), 0)
                        if (currentParentId != 0 && parentSections.any { it.first == currentParentId }) {
                            subCategoryAdapter.setItems(subMenuItems(currentParentId), 0)
                        }
                    } else {
                        // 第三方源保持原始扁平菜单
                        parentSections = emptyList()
                        currentParentId = 0
                        binding.subCategoryList.visibility = View.GONE
                        val leaves = list.filter { it.typePid > 0 }
                        val usable = if (leaves.isNotEmpty()) leaves else list
                        categoryAdapter.setItems(
                            homeMenuItems() + usable.map { it.typeId to it.typeName },
                            0,
                        )
                    }
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
                val seen = mutableSetOf<Int>()
                var lastRawCount = 0
                var batches = 0
                val maxBatches = if (areaIndex > 0) 3 else 1
                val yearFilter = if (yearIndex == 0) null else yearOptions[yearIndex]
                val parentLeaves = if (currentType == 0) emptyList() else leafIdsOf(currentType)
                while (collected.size < 20 && batches < maxBatches) {
                    val raw = when {
                        currentType == 0 -> repository.home(requestedPage)
                        parentLeaves.isNotEmpty() -> parentLeaves.flatMap { leafId ->
                            repository.loadCategory(typeId = leafId, page = requestedPage, year = yearFilter)
                        }
                        else -> repository.loadCategory(
                            typeId = currentType,
                            page = requestedPage,
                            year = yearFilter,
                        )
                    }
                    lastRawCount = raw.size
                    applyFilters(raw).forEach { if (seen.add(it.vodId)) collected.add(it) }
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
