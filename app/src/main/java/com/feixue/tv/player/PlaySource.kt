package com.feixue.tv.player

data class PlaySource(
    val name: String,
    val episodes: List<Episode>,
)

data class Episode(
    val name: String,
    val url: String,
)
