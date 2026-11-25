package com.feixue.tv

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.feixue.tv.data.local.FavoriteStore
import com.feixue.tv.data.model.VodDto
import com.feixue.tv.databinding.ActivityCollectionBinding

class FavoritesActivity : ComponentActivity() {
    private lateinit var binding: ActivityCollectionBinding
    private lateinit var adapter: VodAdapter
    private val items = mutableListOf<FavoriteStore.Item>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.collectionTitle.text = "我的收藏"
        binding.tipText.text = "在详情页点击“加入收藏”添加，长按可取消收藏"
        binding.clearButton.text = "清空收藏"
        binding.backButton.setOnClickListener { finish() }
        binding.clearButton.setOnClickListener {
            if (items.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("清空收藏")
                .setMessage("确定清空全部收藏吗？")
                .setPositiveButton("清空") { _, _ ->
                    FavoriteStore.clear(this)
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
                    .setMessage("取消收藏这部影片？")
                    .setPositiveButton("取消收藏") { _, _ ->
                        FavoriteStore.toggle(
                            this,
                            FavoriteStore.Item(
                                vodId = vod.vodId,
                                sourceId = vod.sourceId,
                                name = vod.vodName.orEmpty(),
                                pic = vod.vodPic.orEmpty(),
                                remark = vod.vodRemarks.orEmpty(),
                            ),
                        )
                        load()
                    }
                    .setNegativeButton("返回", null)
                    .show()
            },
        )
        binding.collectionList.apply {
            layoutManager = GridLayoutManager(this@FavoritesActivity, 6)
            adapter = this@FavoritesActivity.adapter
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        items.clear()
        items.addAll(FavoriteStore.all(this))
        adapter.setItems(
            items.map { item ->
                VodDto(
                    vodId = item.vodId,
                    vodName = item.name,
                    vodPic = item.pic,
                    vodRemarks = item.remark,
                ).apply {
                    sourceId = item.sourceId
                }
            },
        )
        binding.emptyState.text = "暂无收藏\n在详情页点击“加入收藏”即可添加"
        binding.emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }
}
