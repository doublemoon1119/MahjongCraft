package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證麻將骰子的點數循環與安全 fallback。 */
class MahjongDicePointTest {
    /** 驗證一至六依序循環且六點回到一點。 */
    @Test
    fun `points cycle in numeric order`() {
        assertEquals(
            listOf(2, 3, 4, 5, 6, 1),
            MahjongDicePoint.entries.map { it.next().value },
        )
    }

    /** 驗證合法數值逐一還原，非法數值固定回退一點。 */
    @Test
    fun `point values use a deterministic fallback`() {
        MahjongDicePoint.entries.forEach { point ->
            assertEquals(point, MahjongDicePoint.fromValueOrDefault(point.value))
        }
        assertEquals(MahjongDicePoint.ONE, MahjongDicePoint.fromValueOrDefault(0))
        assertEquals(MahjongDicePoint.ONE, MahjongDicePoint.fromValueOrDefault(7))
    }

    /** 驗證標準相反面點數總和皆為七。 */
    @Test
    fun `opposite faces follow standard dice pairs`() {
        assertEquals(7, MahjongDicePoint.ONE.value + MahjongDicePoint.SIX.value)
        assertEquals(7, MahjongDicePoint.TWO.value + MahjongDicePoint.FIVE.value)
        assertEquals(7, MahjongDicePoint.THREE.value + MahjongDicePoint.FOUR.value)
    }
}
