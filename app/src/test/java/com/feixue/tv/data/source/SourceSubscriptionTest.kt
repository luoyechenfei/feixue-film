package com.feixue.tv.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSubscriptionTest {
    @Test
    fun `parse tvbox config keeps only http cms sites`() {
        val json = """
            {
              "sites": [
                {"key":"a","name":"饭太硬CMS","type":1,"api":"https://a.example.com/api.php/provide/vod/"},
                {"key":"b","name":"爬虫源","type":3,"api":"csp_Bili"},
                {"key":"c","name":"分享页","type":1,"api":"https://b.example.com/share/index.html"},
                {"key":"d","name":"B站CMS","type":1,"api":"http://b.example.com/api.php/provide/vod/at/json"},
                {"key":"e","name":"重复","type":1,"api":"http://b.example.com/api.php/provide/vod/at/json"}
              ]
            }
        """.trimIndent()

        val sources = SourceManager.parseSubscription(json)

        assertEquals(2, sources.size)
        assertEquals("饭太硬CMS", sources[0].name)
        assertEquals("https://a.example.com/api.php/provide/vod/at/json", sources[0].url)
        assertEquals("B站CMS", sources[1].name)
        assertEquals("http://b.example.com/api.php/provide/vod/at/json", sources[1].url)
    }

    @Test
    fun `parse legacy json array still works`() {
        val json = """[{"name":"旧订阅","url":"http://c.example.com/api.php/provide/vod"}]"""

        val sources = SourceManager.parseSubscription(json)

        assertEquals(1, sources.size)
        assertEquals("旧订阅", sources[0].name)
        assertEquals("http://c.example.com/api.php/provide/vod/at/json", sources[0].url)
    }

    @Test
    fun `parse plain text lines still works`() {
        val text = """
            # 注释
            我的源,http://d.example.com/api.php/provide/vod/at/json
            http://e.example.com/api.php/provide/vod/at/json
        """.trimIndent()

        val sources = SourceManager.parseSubscription(text)

        assertEquals(2, sources.size)
        assertEquals("我的源", sources[0].name)
        assertEquals("第三方源", sources[1].name)
    }

    @Test
    fun `config url detection`() {
        assertTrue(SourceManager.looksLikeConfigUrl("https://cdn.example.com/tvbox/config.json"))
        assertTrue(SourceManager.looksLikeConfigUrl("http://x.example.com/sub.txt?token=1"))
        assertFalse(SourceManager.looksLikeConfigUrl("http://x.example.com/api.php/provide/vod/at/json"))
    }

    @Test
    fun `parse multi config urls supports urls and configs keys`() {
        val json = """
            {
              "urls": [
                {"name":"仓A","url":"https://a.example.com/1.json"},
                {"name":"仓B","url":"https://b.example.com/2.json"},
                {"name":"无效","url":"csp_xxx"}
              ],
              "configs": [{"url":"https://c.example.com/3.json"}]
            }
        """.trimIndent()

        val subs = SourceManager.parseMultiConfigUrls(json)

        assertEquals(3, subs.size)
        assertEquals("仓A" to "https://a.example.com/1.json", subs[0])
        assertEquals("子仓" to "https://c.example.com/3.json", subs[2])
    }
}
