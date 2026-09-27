package com.doublemoon1119.mahjongcraft.platform.minecraft.stick

import kotlin.test.Test
import kotlin.test.assertEquals

/** 點棒物品資料的面額往返與回退測試。 */
class MahjongScoringStickItemDataTest {
    /** 四種面額皆能以名稱往返。 */
    @Test
    fun `every denomination survives item data round trip`() {
        MahjongScoringStickDenomination.entries.forEach { denomination ->
            assertEquals(denomination, MahjongScoringStickItemData.read(MahjongScoringStickItemData.write(denomination)))
        }
    }

    /** 缺失或無效名稱使用百分棒。 */
    @Test
    fun `missing and invalid denomination names use smallest value`() {
        assertEquals(MahjongScoringStickDenomination.P100, MahjongScoringStickItemData.read(null))
        assertEquals(MahjongScoringStickDenomination.P100, MahjongScoringStickItemData.read("P999"))
    }
}
