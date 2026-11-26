package com.feixue.tv

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.ui.AspectRatioFrameLayout
import com.feixue.tv.databinding.ActivityPlayerBinding
import com.feixue.tv.player.Episode
import com.feixue.tv.player.PlaySource
import com.feixue.tv.settings.AppSettings

class PlayerActivity : ComponentActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var episodes: List<Episode> = emptyList()

    private var engine = AppSettings.ENGINE_EXO
    private var activeEngine = AppSettings.ENGINE_EXO
    private var decodeMode = AppSettings.DECODE_HARD
    private var speed = 1f
    private var introSeconds = 0
    private var outroSeconds = 0
    private var aspectIndex = 0

    private var currentIndex = 0
    private var controlsVisible = false
    private var outroHandled = false

    private var historyVodId = 0
    private var historySourceId = ""
    private var historyPic = ""

    private var audioGroups: List<Tracks.Group> = emptyList()
    private var subtitleGroups: List<Tracks.Group> = emptyList()
    private var videoGroups: List<Tracks.Group> = emptyList()
    private var audioIndex = -1
    private var subtitleIndex = -1
    private var videoIndex = -1

    private val handler = Handler(Looper.getMainLooper())
    private val hideControlsAction = Runnable { hideControls() }

    private val tick = object : Runnable {
        override fun run() {
            updateProgress()
            applyIntroOutro()
            handler.postDelayed(this, 1000L)
        }
    }

    private val softwareCodecSelector = MediaCodecSelector { mimeType, secure, tunneling ->
        val infos = runCatching {
            MediaCodecUtil.getDecoderInfos(mimeType, secure, tunneling)
        }.getOrDefault(emptyList())
        val software = infos.filter { it.softwareOnly }
        if (software.isNotEmpty()) software else infos
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentIndex = player?.currentMediaItemIndex ?: currentIndex
            outroHandled = false
            updateEpisode(currentIndex)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    binding.errorPanel.visibility = View.GONE
                    binding.playerProgress.visibility = View.VISIBLE
                }
                Player.STATE_READY -> {
                    binding.playerProgress.visibility = View.GONE
                    binding.errorPanel.visibility = View.GONE
                    updateEpisode(player?.currentMediaItemIndex ?: currentIndex)
                }
                Player.STATE_ENDED, Player.STATE_IDLE -> {
                    binding.playerProgress.visibility = View.GONE
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            refreshTrackGroups()
        }

        override fun onPlayerError(error: PlaybackException) {
            showError("播放失败，可点击重试，或切换内核/解码方式")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.playerTitle.text = intent.getStringExtra(EXTRA_VOD_NAME).orEmpty()
        binding.playerSource.text = intent.getStringExtra(EXTRA_SOURCE_NAME).orEmpty()
        historyVodId = intent.getIntExtra(EXTRA_VOD_ID, 0)
        historySourceId = intent.getStringExtra(EXTRA_SOURCE_ID).orEmpty()
        historyPic = intent.getStringExtra(EXTRA_PIC).orEmpty()

        engine = AppSettings.engine
        decodeMode = AppSettings.decodeMode
        speed = AppSettings.speed
        introSeconds = AppSettings.introSeconds
        outroSeconds = AppSettings.outroSeconds

        binding.retryButton.setOnClickListener { retryPlayback() }
        binding.playerBackButton.setOnClickListener { finish() }
        setupControls()

        val names = intent.getStringArrayExtra(EXTRA_EPISODE_NAMES).orEmpty()
        val urls = intent.getStringArrayExtra(EXTRA_EPISODE_URLS).orEmpty()
        episodes = urls.mapIndexedNotNull { index, url ->
            if (url.isBlank()) null else {
                Episode(names.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "第${index + 1}集", url)
            }
        }
        if (episodes.isEmpty()) {
            binding.retryButton.visibility = View.GONE
            showError("没有可播放的视频，请返回选择其他播放源")
            return
        }

        currentIndex = intent.getIntExtra(EXTRA_EPISODE_INDEX, 0).coerceIn(0, episodes.lastIndex)
        binding.playerEpisode.text = episodes[currentIndex].name
        refreshLabels()
        startPlayback(0L)
        saveHistory()
        showControls()
    }

    private fun saveHistory() {
        if (historyVodId <= 0) return
        com.feixue.tv.data.local.HistoryStore.save(
            this,
            com.feixue.tv.data.local.HistoryStore.Item(
                vodId = historyVodId,
                sourceId = historySourceId,
                name = binding.playerTitle.text.toString(),
                pic = historyPic,
                sourceName = binding.playerSource.text.toString(),
                episodeIndex = currentIndex,
                episodeName = episodes.getOrNull(currentIndex)?.name.orEmpty(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun setupControls() {
        binding.playPauseButton.setOnClickListener { togglePlayPause() }
        binding.prevEpisodeButton.setOnClickListener { switchEpisode(currentIndex - 1) }
        binding.nextEpisodeButton.setOnClickListener { switchEpisode(currentIndex + 1) }
        binding.episodeButton.setOnClickListener { showEpisodeDialog() }
        binding.replayButton.setOnClickListener {
            outroHandled = false
            seekTo(0L)
            play()
        }
        binding.retryPlayButton.setOnClickListener { retryPlayback() }
        binding.externalButton.setOnClickListener { openExternalPlayer() }

        binding.speedButton.setOnClickListener {
            val options = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
            val index = options.indexOfFirst { kotlin.math.abs(it - speed) < 0.01f }.coerceAtLeast(0)
            speed = options[(index + 1) % options.size]
            AppSettings.speed = speed
            applySpeed()
            refreshLabels()
        }
        binding.aspectButton.setOnClickListener {
            if (activeEngine == AppSettings.ENGINE_IJK) {
                toast("IJK 内核暂不支持画面比例切换")
                return@setOnClickListener
            }
            aspectIndex = (aspectIndex + 1) % 3
            applyAspect()
            refreshLabels()
        }
        binding.engineButton.setOnClickListener {
            engine = when (engine) {
                AppSettings.ENGINE_EXO -> AppSettings.ENGINE_IJK
                AppSettings.ENGINE_IJK -> AppSettings.ENGINE_EXTERNAL
                else -> AppSettings.ENGINE_EXO
            }
            AppSettings.engine = engine
            refreshLabels()
            restartWithCurrentPosition()
        }
        binding.decodeButton.setOnClickListener {
            decodeMode = if (decodeMode == AppSettings.DECODE_HARD) {
                AppSettings.DECODE_SOFT
            } else {
                AppSettings.DECODE_HARD
            }
            AppSettings.decodeMode = decodeMode
            refreshLabels()
            restartWithCurrentPosition()
        }
        binding.subtitleButton.setOnClickListener {
            if (activeEngine != AppSettings.ENGINE_EXO) {
                toast("IJK 内核暂不支持字幕切换")
                return@setOnClickListener
            }
            if (subtitleGroups.isEmpty()) {
                toast("当前视频没有内嵌字幕")
                return@setOnClickListener
            }
            subtitleIndex = if (subtitleIndex >= subtitleGroups.lastIndex) -1 else subtitleIndex + 1
            applySubtitleSelection()
            toast(if (subtitleIndex < 0) "字幕：关闭" else "字幕：${groupLabel(subtitleGroups[subtitleIndex], subtitleIndex, "字幕")}")
        }
        binding.audioButton.setOnClickListener {
            if (activeEngine != AppSettings.ENGINE_EXO) {
                toast("IJK 内核使用默认音轨")
                return@setOnClickListener
            }
            if (audioGroups.isEmpty()) {
                toast("当前视频没有可选音轨")
                return@setOnClickListener
            }
            audioIndex = if (audioIndex >= audioGroups.lastIndex) -1 else audioIndex + 1
            applyAudioSelection()
            val current = audioGroups.getOrNull(audioIndex)?.let { groupLabel(it, audioIndex, "音轨") } ?: "自动"
            toast("声道：$current")
        }
        binding.resolutionButton.setOnClickListener {
            if (activeEngine != AppSettings.ENGINE_EXO) {
                toast("IJK 内核暂不支持分辨率切换")
                return@setOnClickListener
            }
            if (videoGroups.isEmpty()) {
                toast("当前视频没有可选分辨率")
                return@setOnClickListener
            }
            videoIndex = if (videoIndex >= videoGroups.lastIndex) -1 else videoIndex + 1
            applyVideoSelection()
            refreshLabels()
            toast("分辨率：${binding.resolutionButton.text}")
        }
        binding.introButton.setOnClickListener {
            introSeconds = nextSeconds(introSeconds, listOf(0, 5, 10, 15, 20, 30, 45, 60))
            AppSettings.introSeconds = introSeconds
            refreshLabels()
            toast(if (introSeconds == 0) "片头跳过：关闭" else "片头跳过：${introSeconds} 秒")
        }
        binding.outroButton.setOnClickListener {
            outroSeconds = nextSeconds(outroSeconds, listOf(0, 5, 10, 15, 20, 30))
            AppSettings.outroSeconds = outroSeconds
            refreshLabels()
            toast(if (outroSeconds == 0) "片尾跳过：关闭" else "片尾跳过：${outroSeconds} 秒")
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val total = duration()
                if (total > 0) {
                    val target = total * progress / 1000
                    binding.positionText.text = formatTime(target)
                    seekTo(target)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                handler.removeCallbacks(hideControlsAction)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                scheduleHideControls()
            }
        })
    }

    private fun nextSeconds(current: Int, options: List<Int>): Int {
        val index = options.indexOf(current).coerceAtLeast(0)
        return options[(index + 1) % options.size]
    }

    private fun refreshLabels() {
        binding.speedButton.text = AppSettings.speedLabel(speed)
        binding.engineButton.text = if (engine == AppSettings.ENGINE_IJK) "IJK" else "Exo"
        binding.decodeButton.text = if (decodeMode == AppSettings.DECODE_SOFT) "软解" else "硬解"
        binding.aspectButton.text = when (aspectIndex) {
            1 -> "填充"
            2 -> "裁剪"
            else -> "适应"
        }
        binding.resolutionButton.text = videoGroups.getOrNull(videoIndex)
            ?.let { videoLabel(it, videoIndex) } ?: "自动"
        binding.introButton.text = if (introSeconds == 0) "片头" else "片头${introSeconds}s"
        binding.outroButton.text = if (outroSeconds == 0) "片尾" else "片尾${outroSeconds}s"
    }

    private fun applyAspect() {
        binding.playerView.resizeMode = when (aspectIndex) {
            1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private fun showControls() {
        controlsVisible = true
        binding.controlBar.visibility = View.VISIBLE
        binding.infoBar.visibility = View.VISIBLE
        binding.playPauseButton.requestFocus()
        scheduleHideControls()
    }

    private fun hideControls() {
        controlsVisible = false
        binding.controlBar.visibility = View.GONE
        binding.infoBar.visibility = View.GONE
        handler.removeCallbacks(hideControlsAction)
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsAction)
        handler.postDelayed(hideControlsAction, 6000L)
    }

    private fun updateProgress() {
        val position = currentPosition()
        val total = duration()
        binding.positionText.text = formatTime(position)
        binding.durationText.text = formatTime(total)
        if (total > 0) {
            binding.seekBar.progress = (position * 1000 / total).toInt().coerceIn(0, 1000)
        }
        binding.playPauseButton.text = if (isPlaying()) "暂停" else "播放"
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun isPlaying(): Boolean = when (activeEngine) {
        AppSettings.ENGINE_IJK -> binding.ijkView.isPlaying()
        else -> player?.isPlaying == true
    }

    private fun restartWithCurrentPosition() {
        startPlayback(currentPosition())
    }

    private fun startPlayback(resumeMs: Long) {
        releasePlayers()
        binding.errorPanel.visibility = View.GONE
        binding.retryButton.visibility = View.VISIBLE
        handler.removeCallbacks(tick)

        when (engine) {
            AppSettings.ENGINE_IJK -> startIjk(resumeMs)
            AppSettings.ENGINE_EXTERNAL -> {
                showExternalState()
                openExternalPlayer()
            }
            else -> startExo(resumeMs)
        }
        refreshLabels()
        handler.post(tick)
    }

    private fun startExo(resumeMs: Long) {
        activeEngine = AppSettings.ENGINE_EXO
        binding.playerView.visibility = View.VISIBLE
        binding.ijkView.visibility = View.GONE

        val factory = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        if (decodeMode == AppSettings.DECODE_SOFT) {
            factory.setMediaCodecSelector(softwareCodecSelector)
        }
        val exoPlayer = ExoPlayer.Builder(this, factory)
            .setLoadControl(
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    // 起播阈值调小、缓冲上限调大：更快出画面，网络抖动时也不易卡顿
                    .setBufferDurationsMs(20_000, 60_000, 800, 1_500)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
            .build()
        player = exoPlayer
        binding.playerView.player = exoPlayer
        exoPlayer.addListener(playerListener)
        exoPlayer.setHandleAudioBecomingNoisy(true)
        exoPlayer.setPlaybackParameters(PlaybackParameters(speed))
        exoPlayer.setMediaItems(episodes.map { MediaItem.fromUri(it.url) })
        exoPlayer.seekTo(currentIndex, resumeMs.coerceAtLeast(0L))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        binding.playerProgress.visibility = View.VISIBLE
        updateEpisode(currentIndex)
    }

    private fun startIjk(resumeMs: Long) {
        val episode = episodes.getOrNull(currentIndex) ?: return
        if (episode.url.startsWith("https://", true)) {
            // IJK 0.8.8 官方包未内置 openssl，无法直接播放 HTTPS 地址
            engine = AppSettings.ENGINE_EXO
            AppSettings.engine = engine
            refreshLabels()
            toast("当前地址为 HTTPS，IJK 内核不支持，已自动切换 ExoPlayer")
            startExo(resumeMs)
            return
        }
        activeEngine = AppSettings.ENGINE_IJK
        binding.playerView.visibility = View.GONE
        binding.ijkView.visibility = View.VISIBLE
        binding.playerProgress.visibility = View.VISIBLE
        binding.ijkView.onPrepared = {
            binding.playerProgress.visibility = View.GONE
            binding.ijkView.setSpeed(speed)
            if (resumeMs > 0) binding.ijkView.seekTo(resumeMs)
            updateEpisode(currentIndex)
        }
        binding.ijkView.onCompletion = {
            if (currentIndex < episodes.lastIndex) {
                switchEpisode(currentIndex + 1)
            } else {
                showError("播放结束，可返回选择其他剧集或点击重试")
            }
        }
        binding.ijkView.onError = { message ->
            showError("$message\n可尝试切换播放内核或解码方式")
        }
        binding.ijkView.play(episode.url, decodeMode == AppSettings.DECODE_HARD)
        updateEpisode(currentIndex)
    }

    private fun applySpeed() {
        if (activeEngine == AppSettings.ENGINE_IJK) {
            binding.ijkView.setSpeed(speed)
        } else {
            player?.setPlaybackParameters(PlaybackParameters(speed))
        }
    }

    private fun refreshTrackGroups() {
        val exoPlayer = player ?: return
        val tracks = exoPlayer.currentTracks
        audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
        subtitleGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
        videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }
        if (audioIndex > audioGroups.lastIndex) audioIndex = -1
        if (subtitleIndex > subtitleGroups.lastIndex) subtitleIndex = -1
        if (videoIndex > videoGroups.lastIndex) videoIndex = -1
        refreshLabels()
    }

    private fun applyAudioSelection() {
        val exoPlayer = player ?: return
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
        builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        audioGroups.getOrNull(audioIndex)?.let {
            builder.addOverride(TrackSelectionOverride(it.mediaTrackGroup, 0))
        }
        exoPlayer.trackSelectionParameters = builder.build()
    }

    private fun applySubtitleSelection() {
        val exoPlayer = player ?: return
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
        builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitleIndex < 0)
        subtitleGroups.getOrNull(subtitleIndex)?.let {
            builder.addOverride(TrackSelectionOverride(it.mediaTrackGroup, 0))
        }
        exoPlayer.trackSelectionParameters = builder.build()
    }

    private fun applyVideoSelection() {
        val exoPlayer = player ?: return
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
        builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        videoGroups.getOrNull(videoIndex)?.let {
            builder.addOverride(TrackSelectionOverride(it.mediaTrackGroup, 0))
        }
        exoPlayer.trackSelectionParameters = builder.build()
    }

    private fun groupLabel(group: Tracks.Group, index: Int, prefix: String): String {
        val format = group.getTrackFormat(0)
        val parts = mutableListOf<String>()
        format.label?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        format.language?.takeIf { it.isNotBlank() && it != "und" }?.let { parts.add(it) }
        if (format.channelCount > 0) parts.add("${format.channelCount} 声道")
        return parts.joinToString(" · ").ifBlank { "$prefix ${index + 1}" }
    }

    private fun videoLabel(group: Tracks.Group, index: Int): String {
        val format = group.getTrackFormat(0)
        return if (format.height > 0) "${format.height}P" else "轨道${index + 1}"
    }

    private fun updateEpisode(index: Int) {
        episodes.getOrNull(index)?.let { binding.playerEpisode.text = it.name }
    }

    private fun retryPlayback() {
        binding.errorPanel.visibility = View.GONE
        if (activeEngine == AppSettings.ENGINE_IJK) {
            startIjk(currentPosition())
            return
        }
        val exoPlayer = player ?: run {
            startPlayback(currentPosition())
            return
        }
        binding.playerProgress.visibility = View.VISIBLE
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun showError(message: String) {
        binding.playerProgress.visibility = View.GONE
        binding.errorText.text = message
        binding.errorPanel.visibility = View.VISIBLE
        binding.retryButton.visibility = View.VISIBLE
        hideControls()
        binding.retryButton.requestFocus()
    }

    private fun showExternalState() {
        binding.playerProgress.visibility = View.GONE
        binding.errorPanel.visibility = View.VISIBLE
        binding.errorText.text = "已使用外部播放器打开当前剧集\n返回后可在此切换播放内核"
        binding.retryButton.visibility = View.GONE
        hideControls()
        binding.playerBackButton.requestFocus()
    }

    private fun openExternalPlayer() {
        val url = episodes.getOrNull(currentIndex)?.url ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
        }
        runCatching { startActivity(intent) }
            .onFailure { toast("未找到可用的外部播放器，请先安装 VLC 等播放器") }
    }

    private fun showEpisodeDialog() {
        val names = episodes.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择剧集")
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                dialog.dismiss()
                switchEpisode(which)
            }
            .show()
    }

    private fun switchEpisode(index: Int) {
        if (index !in episodes.indices) return
        currentIndex = index
        outroHandled = false
        if (activeEngine == AppSettings.ENGINE_IJK) {
            startIjk(0L)
        } else {
            player?.seekTo(index, 0L)
            player?.play()
        }
        updateEpisode(index)
        saveHistory()
    }

    private fun currentPosition(): Long = when (activeEngine) {
        AppSettings.ENGINE_IJK -> binding.ijkView.currentPosition()
        else -> player?.currentPosition ?: 0L
    }

    private fun duration(): Long = when (activeEngine) {
        AppSettings.ENGINE_IJK -> binding.ijkView.duration()
        else -> player?.duration ?: 0L
    }

    private fun seekTo(positionMs: Long) {
        if (activeEngine == AppSettings.ENGINE_IJK) {
            binding.ijkView.seekTo(positionMs)
        } else {
            player?.seekTo(positionMs)
        }
    }

    private fun applyIntroOutro() {
        val position = currentPosition()
        val total = duration()
        if (introSeconds > 0 && position in 1..(introSeconds * 1000L)) {
            seekTo(introSeconds * 1000L)
            return
        }
        if (outroSeconds > 0 && total > 0 && position > total / 2 &&
            total - position <= outroSeconds * 1000L && !outroHandled
        ) {
            outroHandled = true
            if (currentIndex < episodes.lastIndex) {
                switchEpisode(currentIndex + 1)
            }
        }
    }

    private fun seekBy(offsetMs: Long) {
        val total = duration()
        val target = (currentPosition() + offsetMs).coerceAtLeast(0L)
        seekTo(if (total > 0) target.coerceAtMost(total) else target)
        showControls()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val playing = activeEngine == AppSettings.ENGINE_IJK || player != null
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (controlsVisible) {
                hideControls()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
        if (!playing) return super.onKeyDown(keyCode, event)

        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                if (controlsVisible) hideControls() else showControls()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (!controlsVisible) {
                    showControls()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!controlsVisible) {
                    showControls()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!controlsVisible) {
                    seekBy(-SEEK_STEP_MS)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!controlsVisible) {
                    seekBy(SEEK_STEP_MS)
                    return true
                }
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayPause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                play()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                pause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                switchEpisode(currentIndex + 1)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                switchEpisode(currentIndex - 1)
                return true
            }
        }
        if (controlsVisible) scheduleHideControls()
        return super.onKeyDown(keyCode, event)
    }

    private fun togglePlayPause() {
        if (activeEngine == AppSettings.ENGINE_IJK) {
            if (binding.ijkView.isPlaying()) binding.ijkView.pause() else binding.ijkView.start()
        } else {
            val exoPlayer = player ?: return
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        }
    }

    private fun play() {
        if (activeEngine == AppSettings.ENGINE_IJK) binding.ijkView.start() else player?.play()
    }

    private fun pause() {
        if (activeEngine == AppSettings.ENGINE_IJK) binding.ijkView.pause() else player?.pause()
    }

    private fun releasePlayers() {
        binding.playerView.player = null
        player?.release()
        player = null
        binding.ijkView.release()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onStop() {
        super.onStop()
        pause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(hideControlsAction)
        releasePlayers()
        super.onDestroy()
    }

    companion object {
        private const val SEEK_STEP_MS = 10_000L
        private const val EXTRA_VOD_NAME = "vod_name"
        private const val EXTRA_SOURCE_NAME = "source_name"
        private const val EXTRA_EPISODE_NAMES = "episode_names"
        private const val EXTRA_EPISODE_URLS = "episode_urls"
        private const val EXTRA_EPISODE_INDEX = "episode_index"
        private const val EXTRA_VOD_ID = "vod_id"
        private const val EXTRA_SOURCE_ID = "source_id"
        private const val EXTRA_PIC = "pic"

        fun createIntent(
            context: Context,
            vodName: String,
            source: PlaySource,
            episodeIndex: Int = 0,
            vodId: Int = 0,
            sourceId: String = "",
            pic: String = "",
        ): Intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra(EXTRA_VOD_NAME, vodName)
            putExtra(EXTRA_SOURCE_NAME, source.name)
            putExtra(EXTRA_EPISODE_NAMES, source.episodes.map { it.name }.toTypedArray())
            putExtra(EXTRA_EPISODE_URLS, source.episodes.map { it.url }.toTypedArray())
            putExtra(EXTRA_EPISODE_INDEX, episodeIndex)
            putExtra(EXTRA_VOD_ID, vodId)
            putExtra(EXTRA_SOURCE_ID, sourceId)
            putExtra(EXTRA_PIC, pic)
        }
    }
}
