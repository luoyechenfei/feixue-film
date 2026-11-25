package com.feixue.tv

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.feixue.tv.data.local.HistoryStore
import com.feixue.tv.data.model.VodDto
import com.feixue.tv.databinding.ActivityCollectionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : ComponentActivity() {
    private lateinit var binding: ActivityCollectionBinding
    private lateinit var adapter: VodAdapter
    private val items = mutableListOf<HistoryStore.Item>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.collectionTitle.text = "播放历史"
        binding.tipText.text = "点击进入详情续播，长按可删除该条记录"
        binding.clearButton.text = "清空历史"
        binding.backButton.setOnClickListener { finish() }
        binding.clearButton.setOnClickListener {
            if (items.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("清空历史")
                .setMessage("确定清空全部播放历史吗？")
                .setPositiveButton("清空") { _, _ ->
                    HistoryStore.clear(this)
                    load()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        adapter = VodAdapter(
            onClick = { vod -> DetailActivity.start(this, vod.vodId, vod.sourceId) },
            onLongClick = { vod ->
                AlertDialog.Builder(this)
                    .setTitle(vod.vodName)
                    .setMessage("删除这条播放记录？")
                    .setPositiveButton("删除") { _, _ ->
                        HistoryStore.remove(this, vod.vodId, vod.sourceId)
                        load()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            },
        )
        binding.collectionList.apply {
            layoutManager = GridLayoutManager(this@HistoryActivity, 6)
            adapter = this@HistoryActivity.adapter
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        items.clear()
        items.addAll(HistoryStore.all(this))
        val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        adapter.setItems(
            items.map { item ->
                VodDto(
                    vodId = item.vodId,
                    vodName = item.name,
                    vodPic = item.pic,
                    vodRemarks = "${item.episodeName} · ${timeFormat.format(Date(item.updatedAt))}",
                ).apply {
                    sourceId = item.sourceId
                    sourceName = item.sourceName
                }
            },
        )
        binding.emptyState.text = "暂无播放历史\n观看影片后会自动记录在这里"
        binding.emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }
}
