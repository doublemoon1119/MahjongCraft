package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 查詢與指令共用到完整桌子範圍的距離，而不是到中心的距離。 */
class TableInteractionReachTest {
    /** 桌邊外四點五格仍可達，再遠則不可達，無效座標不通過。 */
    @Test
    fun `reach measures the nearest standard table boundary`() {
        assertEquals(0.0, TableInteractionReach.distance(0.5, 1.0, 0.5, 0, 0, 0))
        assertEquals(4.5, TableInteractionReach.distance(6.5, 1.0, 0.5, 0, 0, 0))
        assertTrue(TableInteractionReach.distance(6.51, 1.0, 0.5, 0, 0, 0) > TableInteractionReach.SURVIVAL)
        assertEquals(Double.POSITIVE_INFINITY, TableInteractionReach.distance(Double.NaN, 1.0, 0.0, 0, 0, 0))
    }
}
