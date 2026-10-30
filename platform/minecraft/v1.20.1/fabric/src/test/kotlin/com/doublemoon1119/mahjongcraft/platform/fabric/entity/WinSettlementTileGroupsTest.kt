package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationTile
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證結算面板的牌組只把露出的牌面交給模板，蓋著的牌只帶牌背。 */
class WinSettlementTileGroupsTest {
    /** 暗槓兩端換成依牌面決定的牌背，模板拿不到蓋著那兩張的牌面；空的手牌組不列入。 */
    @Test
    fun `face down meld tiles become backs without their faces`() {
        val groups = winSettlementTileGroups(
            handAssetKeys = emptyList(),
            melds = listOf(
                WinSettlementMeldSnapshot(listOf("public_east", "east", "east", "public_east"), setOf(0, 3)),
                WinSettlementMeldSnapshot(listOf("p5", "p5", "p5"), emptySet()),
            ),
        ) { asset -> if (asset == "public_east") "example:purple" else "example:rule" }

        assertEquals(
            listOf(
                listOf(PresentationTile.Back("example:purple"), PresentationTile.Face("east"), PresentationTile.Face("east"), PresentationTile.Back("example:purple")),
                List(3) { PresentationTile.Face("p5") },
            ),
            groups,
        )
    }
}
