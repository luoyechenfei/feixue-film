package com.feixue.tv.data.source

data class VideoSource(
    val id: String,
    val name: String,
    val url: String,
    val builtin: Boolean = false,
    val enabled: Boolean = true,
)
