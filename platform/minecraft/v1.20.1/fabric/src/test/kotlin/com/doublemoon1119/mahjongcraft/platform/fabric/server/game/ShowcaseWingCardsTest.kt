package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongMeldTileGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證役滿 showcase 一翼的牌依結算面板的手牌排法排列。 */
class ShowcaseWingCardsTest {
    /** 手牌在前，之後依鳴牌順序接上各組副露；組別依序遞增，暗槓兩端露出牌背。 */
    @Test
    fun `orders the hand before the melds and hides closed kan ends`() {
        val standing = List(2) { Uuid.random() }
        val pon = List(3) { Uuid.random() }
        val addedKan = pon + Uuid.random()
        val closedKan = List(4) { Uuid.random() }
        val melds = listOf(
            MahjongMeldTileGroup(MeldType.ADDED_KAN, addedKan, pon.first(), RelativeDirection.Across, allTilesFaceDown = false),
            MahjongMeldTileGroup(MeldType.CLOSED_KAN, closedKan, null, RelativeDirection.Self, allTilesFaceDown = true),
        )

        val cards = showcaseWingCards(standing, melds) { "asset-$it" }

        assertEquals(standing + addedKan + closedKan, cards.map { it.tileId })
        assertEquals(listOf(0, 0) + List(4) { 1 } + List(4) { 2 }, cards.map { it.group })
        assertEquals(setOf(closedKan.first(), closedKan.last()), cards.filter { it.faceDown }.map { it.tileId }.toSet())
        assertEquals(cards.map { "asset-${it.tileId}" }, cards.map { it.assetKey })
    }

    /** 取不到牌面素材的牌不列入。 */
    @Test
    fun `skips tiles without an asset`() {
        val standing = List(3) { Uuid.random() }

        val cards = showcaseWingCards(standing, emptyList()) { id -> if (id == standing[1]) null else "m1" }

        assertEquals(listOf(standing[0], standing[2]), cards.map { it.tileId })
    }
}
