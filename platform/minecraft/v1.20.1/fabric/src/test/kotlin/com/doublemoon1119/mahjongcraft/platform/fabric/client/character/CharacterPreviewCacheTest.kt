package com.doublemoon1119.mahjongcraft.platform.fabric.client.character

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** 驗證模型圖片有界、延後建立與清理，不需要原生圖形環境。 */
class CharacterPreviewCacheTest {
    /** 載入期間切換世界或重載資源，舊工作回傳的圖片只能釋放，不能進新快取。 */
    @Test
    fun `clear during capture disposes the stale result`() {
        val cache = CharacterPreviewCache<String, Image>(1)
        val stale = Image()
        cache.request("actor")
        cache.processOne {
            cache.clear()
            stale
        }
        assertEquals(1, stale.closes)
        assertNull(cache.request("actor"))
        cache.clear()
        assertEquals(1, stale.closes)
    }

    /** request 不執行工廠，重複鍵也只建立一次。 */
    @Test
    fun `requests are deferred deduplicated and bounded`() {
        val cache = CharacterPreviewCache<String, Image>(2)
        val calls = mutableListOf<String>()
        assertNull(cache.request("first"))
        cache.request("first")
        cache.request("second")
        cache.request("overflow")
        repeat(3) {
            cache.processOne { key ->
                calls += key
                Image()
            }
        }
        assertEquals(listOf("first", "second"), calls)
    }

    /** 讀取會更新 LRU，淘汰與 clear 各自只釋放一次。 */
    @Test
    fun `least recently used image is released and clear cancels pending work`() {
        val cache = CharacterPreviewCache<String, Image>(2)
        val first = Image()
        val second = Image()
        val third = Image()
        cache.request("first")
        cache.processOne { first }
        cache.request("second")
        cache.processOne { second }
        assertSame(first, cache.request("first"))
        cache.request("third")
        cache.processOne { third }
        assertEquals(1, second.closes)
        assertEquals(0, first.closes)
        cache.request("pending")
        cache.clear()
        cache.processOne { error("Cleared pending capture must not execute") }
        assertEquals(1, first.closes)
        assertEquals(1, third.closes)
        cache.clear()
        assertEquals(1, third.closes)
    }

    /** 載入失敗可再次排隊，不把 null 當作永久成功。 */
    @Test
    fun `unavailable image can be requested again`() {
        val cache = CharacterPreviewCache<String, Image>(1)
        cache.request("missing")
        cache.processOne { null }
        cache.request("missing")
        val image = Image()
        cache.processOne { image }
        assertSame(image, cache.request("missing"))
        cache.clear()
    }

    /** 可觀察釋放次數的測試圖片。 */
    private class Image : AutoCloseable {
        /** 已釋放次數。 */
        var closes = 0

        override fun close() {
            closes++
        }
    }
}
