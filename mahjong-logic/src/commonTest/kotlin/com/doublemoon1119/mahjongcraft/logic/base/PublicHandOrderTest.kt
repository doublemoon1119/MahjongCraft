package com.doublemoon1119.mahjongcraft.logic.base

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證公開位置只取決於牌張身分，不受私人順序影響。 */
class PublicHandOrderTest {
    /** 任何輸入排列都產生相同公開順序，而且不改動呼叫端列表。 */
    @Test
    fun `public order is independent of the supplied private order`() {
        val first = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val second = Uuid.parse("00000000-0000-0000-0000-000000000002")
        val privateOrder = mutableListOf(second, first)

        assertEquals(listOf(first, second), PublicHandOrder.arrange(privateOrder))
        assertEquals(listOf(first, second), PublicHandOrder.arrange(privateOrder.reversed()))
        assertEquals(listOf(second, first), privateOrder)
    }
}
