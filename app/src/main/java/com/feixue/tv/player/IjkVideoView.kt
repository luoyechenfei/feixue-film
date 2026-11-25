package com.feixue.tv.player

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import tv.danmaku.ijk.media.player.IjkMediaPlayer

/**
 * 基于 IJKPlayer 的最小视频视图，支持硬解/软解与倍速。
 */
class IjkVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val surfaceView = SurfaceView(context)
    private var mediaPlayer: IjkMediaPlayer? = null
    private var pendingPath: String? = null
    private var pendingHardware = true
    private var playWhenPrepared = true

    var onPrepared: (() -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    init {
        setBackgroundColor(Color.BLACK)
        addView(
            surfaceView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                mediaPlayer?.setDisplay(holder)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                mediaPlayer?.setDisplay(holder)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                mediaPlayer?.setDisplay(null)
            }
        })
    }

    fun play(path: String, hardwareDecode: Boolean) {
        release()
        pendingPath = path
        pendingHardware = hardwareDecode
        val player = IjkMediaPlayer()
        mediaPlayer = player
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", if (hardwareDecode) 1L else 0L)
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1L)
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1L)
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L)
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 1L)
        player.setOnPreparedListener {
            if (playWhenPrepared) it.start()
            onPrepared?.invoke()
        }
        player.setOnCompletionListener {
            onCompletion?.invoke()
        }
        player.setOnErrorListener { _, what, extra ->
            onError?.invoke("IJK 播放错误（$what/$extra）")
            true
        }
        runCatching {
            player.setDataSource(path)
            val surface = surfaceView.holder.surface
            if (surface != null && surface.isValid) player.setDisplay(surfaceView.holder)
            player.prepareAsync()
        }.onFailure {
            onError?.invoke("IJK 播放失败：${it.message}")
        }
    }

    fun start() {
        playWhenPrepared = true
        mediaPlayer?.start()
    }

    fun pause() {
        playWhenPrepared = false
        mediaPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs)
    }

    fun currentPosition(): Long = mediaPlayer?.currentPosition ?: 0L

    fun duration(): Long = mediaPlayer?.duration ?: 0L

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun setSpeed(speed: Float) {
        val player = mediaPlayer ?: return
        runCatching {
            val method = player.javaClass.getMethod("setSpeed", Float::class.javaPrimitiveType)
            method.invoke(player, speed)
        }.onFailure {
            runCatching {
                player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "speed", (speed * 100).toLong())
            }
        }
    }

    fun release() {
        mediaPlayer?.let { player ->
            runCatching { player.stop() }
            runCatching { player.setDisplay(null) }
            runCatching { player.release() }
        }
        mediaPlayer = null
        pendingPath = null
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }
}
