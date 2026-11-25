package com.feixue.tv.settings

import android.content.Context
import android.content.SharedPreferences

object AppSettings {
    const val ENGINE_EXO = "exo"
    const val ENGINE_IJK = "ijk"
    const val ENGINE_EXTERNAL = "external"

    const val DECODE_HARD = "hard"
    const val DECODE_SOFT = "soft"

    private const val PREFS_NAME = "feixue_settings"
    private const val KEY_ENGINE = "player_engine"
    private const val KEY_DECODE = "decode_mode"
    private const val KEY_SPEED = "playback_speed"
    private const val KEY_INTRO = "intro_seconds"
    private const val KEY_OUTRO = "outro_seconds"
    private const val KEY_QR_PORT = "qr_push_port"
    private const val KEY_AGGREGATE_SEARCH = "aggregate_search"
    private const val KEY_AUTO_BEST = "auto_best_source"
    private const val KEY_SHOW_RECOMMEND = "show_recommend"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun required(): SharedPreferences {
        check(::prefs.isInitialized) { "AppSettings.init() must be called first" }
        return prefs
    }

    var engine: String
        get() = required().getString(KEY_ENGINE, ENGINE_EXO) ?: ENGINE_EXO
        set(value) = required().edit().putString(KEY_ENGINE, value).apply()

    var decodeMode: String
        get() = required().getString(KEY_DECODE, DECODE_HARD) ?: DECODE_HARD
        set(value) = required().edit().putString(KEY_DECODE, value).apply()

    var speed: Float
        get() = required().getFloat(KEY_SPEED, 1f)
        set(value) = required().edit().putFloat(KEY_SPEED, value).apply()

    var introSeconds: Int
        get() = required().getInt(KEY_INTRO, 0)
        set(value) = required().edit().putInt(KEY_INTRO, value).apply()

    var outroSeconds: Int
        get() = required().getInt(KEY_OUTRO, 0)
        set(value) = required().edit().putInt(KEY_OUTRO, value).apply()

    var qrPushPort: Int
        get() = required().getInt(KEY_QR_PORT, 8787)
        set(value) = required().edit().putInt(KEY_QR_PORT, value).apply()

    var aggregateSearch: Boolean
        get() = required().getBoolean(KEY_AGGREGATE_SEARCH, true)
        set(value) = required().edit().putBoolean(KEY_AGGREGATE_SEARCH, value).apply()

    var autoBestSource: Boolean
        get() = required().getBoolean(KEY_AUTO_BEST, true)
        set(value) = required().edit().putBoolean(KEY_AUTO_BEST, value).apply()

    var showRecommend: Boolean
        get() = required().getBoolean(KEY_SHOW_RECOMMEND, true)
        set(value) = required().edit().putBoolean(KEY_SHOW_RECOMMEND, value).apply()

    fun engineLabel(value: String = engine): String = when (value) {
        ENGINE_IJK -> "IJK 播放器"
        ENGINE_EXTERNAL -> "外部播放器"
        else -> "ExoPlayer"
    }

    fun decodeLabel(value: String = decodeMode): String =
        if (value == DECODE_SOFT) "软解码" else "硬解码"

    fun speedLabel(value: Float = speed): String {
        val text = if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
        return "${text}x"
    }
}
