package com.feixue.tv

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import coil.load
import com.feixue.tv.data.local.FavoriteStore
import com.feixue.tv.data.model.VodDto
import com.feixue.tv.data.remote.ApiClient
import com.feixue.tv.data.repository.VodRepository
import com.feixue.tv.data.source.SourceManager
import com.feixue.tv.databinding.ActivityDetailBinding
import com.feixue.tv.player.PlaySource
import com.feixue.tv.player.PlaySourceParser
import com.feixue.tv.settings.AppSettings
import kotlinx.coroutines.launch
import okhttp3.Request
import java.util.concurrent.TimeUnit

class DetailActivity : ComponentActivity() {
    private lateinit var binding: ActivityDetailBinding
    private val repository by lazy {
        VodRepository(SourceManager.byId(intent.getStringExtra(EXTRA_SOURCE_ID).orEmpty()))
    }
    private lateinit var episodeAdapter: TextOptionAdapter

    private val speedClient = ApiClient.okHttpClient.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    private var sources: List<PlaySource> = emptyList()
    private var currentVod: VodDto? = null
    private var selectedSourceIndex = 0
    private var selectedEpisodeIndex = 0
    private var userPickedSource = false
    private var detailLoaded = false
    private var reversed = false

    private val vodId: Int get() = intent.getIntExtra(EXTRA_VOD_ID, 0)
    private val sourceId: String get() = intent.getStringExtra(EXTRA_SOURCE_ID).orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        episodeAdapter = TextOptionAdapter { id, _ ->
            selectedEpisodeIndex = id
            playSelected()
        }
        binding.episodeList.apply {
            layoutManager = GridLayoutManager(this@DetailActivity, 8)
            adapter = episodeAdapter
        }

        binding.backButton.setOnClickListener { finish() }
        binding.detailState.setOnClickListener { loadDetail() }
        binding.quickSearchButton.setOnClickListener {
            SearchActivity.start(this, currentVod?.vodName.orEmpty())
        }
        binding.reverseButton.setOnClickListener {
            reversed = !reversed
            binding.reverseButton.text = if (reversed) "正序" else "倒序"
            refreshEpisodes()
        }
        binding.favoriteButton.setOnClickListener {
            val vod = currentVod ?: return@setOnClickListener
            val favored = FavoriteStore.toggle(
                this,
                FavoriteStore.Item(
                    vodId = vod.vodId,
                    sourceId = sourceId,
                    name = vod.vodName.orEmpty(),
                    pic = vod.vodPic.orEmpty(),
                    remark = vod.vodRemarks.orEmpty(),
                ),
            )
            updateFavoriteButton()
            Toast.makeText(this, if (favored) "已加入收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
        }
        binding.introButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(currentVod?.vodName.orEmpty())
                .setMessage(currentVod?.let { plainDescription(it) } ?: "暂无剧情简介")
                .setPositiveButton("关闭", null)
                .show()
        }

        loadDetail()
    }

    private fun loadDetail() {
        binding.detailProgress.visibility = View.VISIBLE
        binding.detailState.visibility = View.GONE
        binding.detailContent.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val vod = repository.detail(vodId) ?: throw IllegalStateException("未找到影片详情")
                if (!isFinishing) renderDetail(vod)
            } catch (error: Exception) {
                if (!isFinishing) {
                    binding.detailState.text = "详情加载失败，点击重试"
                    binding.detailState.visibility = View.VISIBLE
                }
            } finally {
                binding.detailProgress.visibility = View.GONE
            }
        }
    }

    private fun renderDetail(vod: VodDto) {
        detailLoaded = true
        currentVod = vod
        binding.detailContent.visibility = View.VISIBLE
        binding.detailTitle.text = vod.vodName?.takeIf { it.isNotBlank() } ?: "未命名影片"
        val displaySource = vod.sourceName.ifBlank {
            SourceManager.byId(sourceId)?.name ?: SourceManager.active().name
        }
        binding.sourceMetaText.text = buildString {
            append("来源：").append(displaySource)
            append("  年份：").append(vod.vodYear?.takeIf { it.isNotBlank() } ?: "-")
            append("  地区：").append(vod.vodArea?.takeIf { it.isNotBlank() } ?: "-")
        }
        binding.actorText.text = "演员：${vod.vodActor?.takeIf { it.isNotBlank() } ?: "暂无"}"
        binding.directorText.text = "导演：${vod.vodDirector?.takeIf { it.isNotBlank() } ?: "暂无"}"
        binding.descriptionText.text = "内容简介：${plainDescription(vod)}"

        binding.detailPoster.load(vod.vodPic?.takeIf { it.isNotBlank() }) {
            placeholder(R.drawable.bg_poster_placeholder)
            error(R.drawable.bg_poster_placeholder)
            fallback(R.drawable.bg_poster_placeholder)
            crossfade(true)
        }
        updateFavoriteButton()

        sources = PlaySourceParser.parse(vod)
        if (sources.isEmpty()) {
            binding.episodeTitle.text = "当前源无可用线路，可尝试其他数据源"
            renderSourceTabs()
            episodeAdapter.setItems(emptyList())
            return
        }

        selectedSourceIndex = sources.indexOf(PlaySourceParser.selectPreferred(sources)).coerceAtLeast(0)
        userPickedSource = false
        selectSource(selectedSourceIndex)
        measureBestSource()
    }

    private fun renderSourceTabs(bestIndex: Int = -1) {
        binding.sourceTabs.removeAllViews()
        sources.forEachIndexed { index, source ->
            val selected = index == selectedSourceIndex
            val tab = TextView(this).apply {
                text = if (index == bestIndex) "${source.name}·最优" else source.name
                textSize = 14f
                setPadding(dp(8), dp(6), dp(8), dp(6))
                isFocusable = true
                isClickable = true
                setTextColor(
                    ContextCompat.getColor(
                        this@DetailActivity,
                        if (selected) R.color.accent else R.color.text_secondary,
                    ),
                )
                paintFlags = if (selected) {
                    paintFlags or Paint.UNDERLINE_TEXT_FLAG
                } else {
                    paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
                }
                setOnClickListener {
                    userPickedSource = true
                    selectSource(index)
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp(10) }
            binding.sourceTabs.addView(tab, params)
        }
    }

    private fun selectSource(index: Int) {
        val source = sources.getOrNull(index) ?: return
        selectedSourceIndex = index
        selectedEpisodeIndex = selectedEpisodeIndex.coerceIn(0, source.episodes.lastIndex)
        renderSourceTabs()
        binding.playUrlText.text = "播放地址：${source.episodes.getOrNull(selectedEpisodeIndex)?.url.orEmpty()}"
        binding.episodeTitle.text = "选集（${source.name} · ${source.episodes.size} 集）"
        refreshEpisodes()
    }

    private fun refreshEpisodes() {
        val source = sources.getOrNull(selectedSourceIndex) ?: return
        val items = source.episodes.mapIndexed { index, episode -> index to episode.name }
        episodeAdapter.setItems(if (reversed) items.reversed() else items, selectedEpisodeIndex)
    }

    private fun updateFavoriteButton() {
        binding.favoriteButton.text = if (FavoriteStore.contains(this, vodId, sourceId)) {
            "已收藏"
        } else {
            "加入收藏"
        }
    }

    private fun measureBestSource() {
        if (!AppSettings.autoBestSource || sources.size < 2) return
        val candidates = sources.mapIndexedNotNull { index, source ->
            source.episodes.firstOrNull()?.url?.let { index to it }
        }
        if (candidates.size < 2) return

        lifecycleScope.launch {
            // 所有线路并行测速，谁最先响应成功就用谁，不再等待最慢的一条
            val channel = kotlinx.coroutines.channels.Channel<Int>(kotlinx.coroutines.channels.Channel.UNLIMITED)
            val jobs = candidates.map { (index, url) ->
                launch {
                    val ok = runCatching {
                        speedClient.newCall(
                            Request.Builder().url(url).header("Range", "bytes=0-2047").build(),
                        ).execute().use { it.isSuccessful }
                    }.getOrDefault(false)
                    if (ok) channel.send(index)
                }
            }
            val best = kotlinx.coroutines.withTimeoutOrNull(BEST_SOURCE_TIMEOUT_MS) {
                channel.receive()
            }
            jobs.forEach { it.cancel() }
            channel.close()

            if (best != null && !userPickedSource && detailLoaded && !isFinishing) {
                selectSource(best)
                renderSourceTabs(best)
                Toast.makeText(
                    this@DetailActivity,
                    "已选择最优线路：${sources[best].name}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun playSelected() {
        val source = sources.getOrNull(selectedSourceIndex) ?: return
        if (source.episodes.isEmpty()) {
            Toast.makeText(this, "当前播放源没有可播放剧集", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            PlayerActivity.createIntent(
                this,
                currentVod?.vodName.orEmpty(),
                source,
                selectedEpisodeIndex.coerceIn(0, source.episodes.lastIndex),
                vodId = vodId,
                sourceId = sourceId,
                pic = currentVod?.vodPic.orEmpty(),
            ),
        )
    }

    private fun plainDescription(vod: VodDto): String {
        val raw = vod.vodContent?.takeIf { it.isNotBlank() } ?: return "暂无剧情简介"
        return androidx.core.text.HtmlCompat
            .fromHtml(raw, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString().trim()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_VOD_ID = "vod_id"
        private const val EXTRA_SOURCE_ID = "source_id"
        private const val BEST_SOURCE_TIMEOUT_MS = 2_500L

        fun start(context: Context, vodId: Int, sourceId: String = "") {
            context.startActivity(
                Intent(context, DetailActivity::class.java).apply {
                    putExtra(EXTRA_VOD_ID, vodId)
                    putExtra(EXTRA_SOURCE_ID, sourceId)
                },
            )
        }
    }
}
