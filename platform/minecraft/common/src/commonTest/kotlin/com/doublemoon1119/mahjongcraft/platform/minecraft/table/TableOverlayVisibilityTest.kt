package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 常駐資訊面板的隱藏期限與解除邊界。 */
class TableOverlayVisibilityTest {
    /** 沒有隱藏設定時，正常遊戲時間均可顯示。 */
    @Test
    fun `unset deadline keeps the overlay visible`() {
        assertFalse(TableOverlayVisibility.isHidden(Long.MIN_VALUE, 0L))
        assertFalse(TableOverlayVisibility.isHidden(Long.MIN_VALUE, Long.MAX_VALUE))
    }

    /** 正好到期即恢復，不額外多隱藏一個 tick。 */
    @Test
    fun `finite deadline hides only before its exact expiry`() {
        assertTrue(TableOverlayVisibility.isHidden(100L, 99L))
        assertFalse(TableOverlayVisibility.isHidden(100L, 100L))
        assertFalse(TableOverlayVisibility.isHidden(100L, 101L))
    }

    /** 無期限隱藏使用明確的哨兵值，不會因時間到最大值而解除。 */
    @Test
    fun `hidden until removal remains hidden at every game time`() {
        assertTrue(TableOverlayVisibility.isHidden(Long.MAX_VALUE, 0L))
        assertTrue(TableOverlayVisibility.isHidden(Long.MAX_VALUE, Long.MAX_VALUE))
    }

    /** 重複要求只能延長，有限期限不能蓋掉隱藏到移除的狀態。 */
    @Test
    fun `requests extend but never shorten the current deadline`() {
        assertEquals(100L, TableOverlayVisibility.extend(Long.MIN_VALUE, 100L))
        assertEquals(100L, TableOverlayVisibility.extend(100L, 50L))
        assertEquals(100L, TableOverlayVisibility.extend(100L, 100L))
        assertEquals(200L, TableOverlayVisibility.extend(100L, 200L))
        assertEquals(Long.MAX_VALUE, TableOverlayVisibility.extend(Long.MAX_VALUE, 200L))
    }
}
