package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證捨牌候選依完整牌值區分，不因牌面相同而漏掉特性不同的選擇。 */
class HandDiscardCandidatesTest {
    /** 同牌面的普通牌與特殊牌都要保留，相同特性的重複牌只取一次。 */
    @Test
    fun `discard candidates preserve component differences and ignore tile identity`() {
        val five = Tile.Numeric(Tile.Suit.Dot, 5)
        val special = ComponentSet.EMPTY.with(bonusComponent, Unit)
        val hand = Hand(
            tiles = listOf(
                IdentifiedTile(Uuid.random(), five),
                IdentifiedTile(Uuid.random(), five),
                IdentifiedTile(id = Uuid.random(), tile = five, components = special),
                IdentifiedTile(id = Uuid.random(), tile = five, components = special),
            ),
        )

        assertEquals(listOf(0, 2), distinctDiscardIndices(hand))
    }

    /** 將牌面相同的特殊牌換成普通牌後，候選也必須依最新的完整牌值更新。 */
    @Test
    fun `discard candidates use current components rather than a tile id cache`() {
        val five = Tile.Numeric(Tile.Suit.Dot, 5)
        val first = IdentifiedTile(Uuid.random(), five)
        val second = IdentifiedTile(id = Uuid.random(), tile = five, components = ComponentSet.EMPTY.with(bonusComponent, Unit))
        val hand = Hand(tiles = listOf(first, second))

        assertEquals(listOf(0, 1), distinctDiscardIndices(hand))
        assertEquals(listOf(0), distinctDiscardIndices(hand.copy(tiles = listOf(first, second.copy(components = ComponentSet.EMPTY)))))
    }

    /** 測試共用的牌值特性定義。 */
    private companion object {
        /** 測試規則的額外牌值特性，不改變基本牌面。 */
        val bonusComponent = ComponentType(
            id = "test:tile/bonus",
            valueClass = Unit::class,
            copyValue = {},
            target = BuiltInComponentOwners.TILE,
        )
    }
}
