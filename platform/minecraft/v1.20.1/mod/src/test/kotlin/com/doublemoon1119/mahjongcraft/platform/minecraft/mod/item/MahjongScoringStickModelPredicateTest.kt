package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.item

import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickDenomination
import kotlin.test.Test
import kotlin.test.assertEquals

/** 點棒面額 model predicate 的測試。 */
class MahjongScoringStickModelPredicateTest {
    /** 四種面額在夾限後仍對應四個不同的模型值。 */
    @Test
    fun `model predicate values remain distinct within unit interval`() {
        val values = MahjongScoringStickDenomination.entries.map { it.toModelPredicateValue() }
        assertEquals(values.size, values.distinct().size)
        assertEquals(0.0f, values.first())
        assertEquals(1.0f, values.last())
    }
}
