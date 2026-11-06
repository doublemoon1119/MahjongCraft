package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證本人手牌排列時間軸依時間取用項目。 */
class PrivateHandOrderTimelineTest {
    /** 測試用的牌。 */
    private val tileId = Uuid.parse("00000000-0000-0000-0000-000000000001")

    /** 還沒生效的項目不會提早套用；之後取生效時間不晚於現在的最後一個。 */
    @Test
    fun `entries apply from their effective time`() {
        val timeline = PrivateHandOrderTimeline()
        val gap = order(listOf(null))
        val filled = order(listOf(tileId))
        timeline.add(PrivateHandOrderEntry(effectiveAt = 10L, order = gap))
        timeline.add(PrivateHandOrderEntry(effectiveAt = 20L, order = filled))

        assertNull(timeline.orderAt(9.5))
        assertEquals(gap, timeline.orderAt(10.0))
        assertEquals(gap, timeline.orderAt(19.9))
        assertEquals(filled, timeline.orderAt(25.0))
        assertEquals(filled, timeline.orderAt(25.0), "The latest effective entry stays after older ones are dropped.")
    }

    /** 不依時間順序抵達的項目依生效時間排列；同一時間以後到的為準。 */
    @Test
    fun `entries arriving out of order are placed by time`() {
        val timeline = PrivateHandOrderTimeline()
        val early = order(listOf(null))
        val late = order(listOf(tileId))
        val replaced = order(emptyList())
        timeline.add(PrivateHandOrderEntry(effectiveAt = 20L, order = late))
        timeline.add(PrivateHandOrderEntry(effectiveAt = 10L, order = early))

        assertEquals(early, timeline.orderAt(15.0))
        timeline.add(PrivateHandOrderEntry(effectiveAt = 20L, order = replaced))
        assertEquals(replaced, timeline.orderAt(20.0))
    }

    /** 沒有任何項目時呼叫端改用快照；有項目但還沒生效時不是空的。 */
    @Test
    fun `empty only before any entry arrives`() {
        val timeline = PrivateHandOrderTimeline()
        assertTrue(timeline.isEmpty)
        timeline.add(PrivateHandOrderEntry(effectiveAt = 100L, order = order(emptyList())))

        assertFalse(timeline.isEmpty)
        assertNull(timeline.orderAt(0.0))
    }

    /** 組成只有立牌的排列。 */
    private fun order(standing: List<Uuid?>) = PrivateHandOrder(standing = standing, drawnId = null, unsortedIds = emptyList())
}
