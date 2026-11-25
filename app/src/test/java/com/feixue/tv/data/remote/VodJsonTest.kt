package com.feixue.tv.data.remote

import com.feixue.tv.data.model.VodResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VodJsonTest {
    private val gson = createVodGson()

    @Test
    fun `typical Apple CMS response parses mixed scalar types safely`() {
        val json = """
            {
              "code": 1,
              "msg": "数据列表",
              "page": "1",
              "pagecount": "3",
              "limit": 20,
              "total": 42,
              "list": [
                {
                  "vod_id": "1001",
                  "type_id": 1,
                  "type_name": "电影",
                  "vod_name": 2026001,
                  "vod_pic": null,
                  "vod_remarks": 8.0,
                  "vod_year": 2026,
                  "vod_area": true,
                  "vod_lang": "国语",
                  "vod_type": "动作",
                  "vod_class": "喜剧",
                  "vod_content": 12345,
                  "vod_actor": "演员甲,演员乙",
                  "vod_director": "导演甲",
                  "vod_play_from": "m3u8",
                  "vod_play_url": "https://cdn.example.com/movie.m3u8"
                }
              ],
              "class": [
                {"type_id": "1", "type_name": 123, "type_pid": 0}
              ]
            }
        """.trimIndent()

        val response = gson.fromJson(json, VodResponse::class.java)
        val vod = response.list.single()

        assertEquals(1, response.code)
        assertEquals("1", response.page)
        assertEquals(3, response.pagecount)
        assertEquals("20", response.limit)
        assertEquals(42, response.total)
        assertEquals(1001, vod.vodId)
        assertEquals("2026001", vod.vodName)
        assertNull(vod.vodPic)
        assertEquals("8.0", vod.vodRemarks)
        assertEquals("2026", vod.vodYear)
        assertEquals("true", vod.vodArea)
        assertEquals("12345", vod.vodContent)
        assertEquals("m3u8", vod.vodPlayFrom)
        assertEquals(1, response.categories?.single()?.typeId)
        assertEquals("123", response.categories?.single()?.typeName)
    }

    @Test
    fun `object and array values become null strings instead of crashing`() {
        val json = """
            {
              "code": 1,
              "page": 1,
              "pagecount": 1,
              "limit": "20",
              "total": 1,
              "list": [
                {"vod_id": 1, "vod_name": {"title": "bad"}, "vod_content": ["bad"]}
              ]
            }
        """.trimIndent()

        val vod = gson.fromJson(json, VodResponse::class.java).list.single()

        assertNull(vod.vodName)
        assertNull(vod.vodContent)
    }

    @Test
    fun `empty list and null categories are supported`() {
        val response = gson.fromJson(
            """{"code":1,"page":1,"pagecount":0,"limit":20,"total":0,"list":[],"class":null}""",
            VodResponse::class.java,
        )

        assertTrue(response.list.isEmpty())
        assertNull(response.categories)
    }
}
