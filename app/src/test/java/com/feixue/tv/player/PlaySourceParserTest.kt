package com.feixue.tv.player

import com.feixue.tv.data.model.VodDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaySourceParserTest {
    @Test
    fun `parse returns all sources and episodes in original order`() {
        val sources = parse(
            "m3u8${'$'}${'$'}${'$'}backup",
            "第01集${'$'}https://cdn.example.com/1.m3u8#第02集${'$'}https://cdn.example.com/2.m3u8" +
                "${'$'}${'$'}${'$'}正片${'$'}https://cdn.example.com/movie.mp4",
        )

        assertEquals(2, sources.size)
        assertEquals("m3u8", sources[0].name)
        assertEquals(listOf("第01集", "第02集"), sources[0].episodes.map(Episode::name))
        assertEquals("https://cdn.example.com/2.m3u8", sources[0].episodes[1].url)
        assertEquals("backup", sources[1].name)
        assertEquals("正片", sources[1].episodes.single().name)
    }

    @Test
    fun `selectPreferred prefers source name containing m3u8 ignoring case`() {
        val sources = parse(
            "mp4${'$'}${'$'}${'$'}My_M3U8_Source",
            "正片${'$'}https://cdn.example.com/movie.mp4${'$'}${'$'}${'$'}正片${'$'}https://cdn.example.com/movie.mp4",
        )

        assertEquals("My_M3U8_Source", PlaySourceParser.selectPreferred(sources)?.name)
    }

    @Test
    fun `selectPreferred detects m3u8 URL even when source name does not`() {
        val sources = parse(
            "mp4${'$'}${'$'}${'$'}backup",
            "正片${'$'}https://cdn.example.com/movie.mp4" +
                "${'$'}${'$'}${'$'}正片${'$'}https://cdn.example.com/live/index.M3U8?token=abc",
        )

        assertEquals("backup", PlaySourceParser.selectPreferred(sources)?.name)
    }

    @Test
    fun `selectPreferred falls back to first valid source`() {
        val sources = parse(
            "invalid${'$'}${'$'}${'$'}mp4",
            "正片${'$'}ftp://cdn.example.com/movie.mp4${'$'}${'$'}${'$'}正片${'$'}https://cdn.example.com/movie.mp4",
        )

        assertEquals(1, sources.size)
        assertEquals("mp4", PlaySourceParser.selectPreferred(sources)?.name)
    }

    @Test
    fun `missing titles and URLs are handled and URL without title is supported`() {
        val sources = parse(
            "mp4",
            "https://cdn.example.com/1.mp4#${'$'}https://cdn.example.com/2.mp4" +
                "#第3集${'$'}#第4集${'$'}not-a-url",
        )

        val episodes = sources.single().episodes
        assertEquals(2, episodes.size)
        assertEquals("第1集", episodes[0].name)
        assertEquals("第2集", episodes[1].name)
        assertEquals("https://cdn.example.com/1.mp4", episodes[0].url)
        assertEquals("https://cdn.example.com/2.mp4", episodes[1].url)
    }

    @Test
    fun `source and URL count mismatch does not lose valid URLs`() {
        val fewerUrls = parse(
            "one${'$'}${'$'}${'$'}two${'$'}${'$'}${'$'}three",
            "正片${'$'}https://cdn.example.com/1.mp4",
        )
        val fewerNames = parse(
            "one",
            "正片${'$'}https://cdn.example.com/1.mp4${'$'}${'$'}${'$'}https://cdn.example.com/2.mp4",
        )

        assertEquals(1, fewerUrls.size)
        assertEquals(2, fewerNames.size)
        assertEquals("播放源2", fewerNames[1].name)
        assertEquals("https://cdn.example.com/2.mp4", fewerNames[1].episodes.single().url)
    }

    @Test
    fun `share pages and empty items are filtered`() {
        val sources = parse(
            "web${'$'}${'$'}${'$'}video${'$'}${'$'}${'$'}empty",
            "页面${'$'}https://example.com/share/123.html" +
                "${'$'}${'$'}${'$'}#第1集${'$'}https://cdn.example.com/video.m3u8#${'$'}${'$'}${'$'}",
        )

        assertEquals(1, sources.size)
        assertEquals("video", sources.single().name)
        assertEquals(1, sources.single().episodes.size)
    }

    @Test
    fun `only share pages produce no playable sources`() {
        val sources = parse(
            "web${'$'}${'$'}${'$'}web2",
            "播放${'$'}https://example.com/share/abc${'$'}${'$'}${'$'}播放${'$'}http://example.com/SHARE/def",
        )

        assertTrue(sources.isEmpty())
        assertNull(PlaySourceParser.selectPreferred(sources))
    }

    @Test
    fun `blank play URL produces empty source list`() {
        assertTrue(parse("m3u8", "  ").isEmpty())
        assertTrue(parse(null, null).isEmpty())
    }

    private fun parse(playFrom: String?, playUrl: String?): List<PlaySource> =
        PlaySourceParser.parse(
            VodDto(vodPlayFrom = playFrom, vodPlayUrl = playUrl),
        )
}
