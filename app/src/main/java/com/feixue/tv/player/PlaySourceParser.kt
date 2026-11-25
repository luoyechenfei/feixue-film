package com.feixue.tv.player

import com.feixue.tv.data.model.VodDto

object PlaySourceParser {
    private const val SOURCE_SEPARATOR = "$$$"
    private const val EPISODE_SEPARATOR = '#'
    private const val TITLE_SEPARATOR = '$'

    fun parse(vod: VodDto): List<PlaySource> = parse(vod.vodPlayFrom, vod.vodPlayUrl)

    fun parse(playFrom: String?, playUrl: String?): List<PlaySource> {
        if (playUrl.isNullOrBlank()) return emptyList()

        val names = playFrom.orEmpty().split(SOURCE_SEPARATOR)
        val urlGroups = playUrl.split(SOURCE_SEPARATOR)
        return (0 until maxOf(names.size, urlGroups.size)).mapNotNull { sourceIndex ->
            val episodes = urlGroups.getOrNull(sourceIndex)
                .orEmpty()
                .split(EPISODE_SEPARATOR)
                .mapIndexedNotNull { episodeIndex, value -> parseEpisode(value, episodeIndex) }
            if (episodes.isEmpty()) return@mapNotNull null

            PlaySource(
                name = names.getOrNull(sourceIndex)?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: "播放源${sourceIndex + 1}",
                episodes = episodes,
            )
        }
    }

    fun selectPreferred(sources: List<PlaySource>): PlaySource? =
        sources.firstOrNull(::isM3u8Source) ?: sources.firstOrNull()

    private fun parseEpisode(value: String, index: Int): Episode? {
        val item = value.trim()
        if (item.isEmpty()) return null

        val separatorIndex = item.indexOf(TITLE_SEPARATOR)
        val title = if (separatorIndex >= 0) {
            item.substring(0, separatorIndex).trim()
        } else {
            ""
        }
        val url = if (separatorIndex >= 0) {
            item.substring(separatorIndex + 1).trim()
        } else {
            item
        }
        if (!isPlayableUrl(url)) return null

        return Episode(
            name = title.ifEmpty { "第${index + 1}集" },
            url = url,
        )
    }

    private fun isM3u8Source(source: PlaySource): Boolean =
        source.name.contains("m3u8", ignoreCase = true) ||
            source.episodes.any { it.url.contains(".m3u8", ignoreCase = true) }

    private fun isPlayableUrl(url: String): Boolean {
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }

        val pathWithoutQuery = url.substringBefore('?').substringBefore('#')
        return pathWithoutQuery.split('/').none { it.equals("share", ignoreCase = true) }
    }
}
