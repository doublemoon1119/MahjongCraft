package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongMeldTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證役滿 showcase 一翼的牌依結算面板的手牌排法排列。 */
class ShowcaseWingCardsTest {
    /** 手牌在前，之後依鳴牌順序接上各組副露；組別依序遞增，暗槓兩端只帶依牌面決定的牌背，不帶牌面。 */
    @Test
    fun `orders the hand before the melds and hides closed kan ends`() {
        val standing = List(2) { Uuid.random() }
        val pon = List(3) { Uuid.random() }
        val addedKan = pon + Uuid.random()
        val closedKan = List(4) { Uuid.random() }
        val melds = listOf(
            MahjongMeldTileGroup(MeldType.ADDED_KAN, addedKan, pon.first(), RelativeDirection.Across, allTilesFaceDown = false, faceDownTileIds = emptySet()),
            MahjongMeldTileGroup(
                type = MeldType.CLOSED_KAN,
                tileIds = closedKan,
                calledTileId = null,
                sourceDirection = RelativeDirection.Self,
                allTilesFaceDown = false,
                faceDownTileIds = setOf(closedKan.first(), closedKan.last()),
            ),
        )

        val cards = showcaseWingCards(
            standingTileIds = standing,
            melds = melds,
            assetKeyOf = { "asset-$it" },
            backAppearanceOf = { asset -> "back-of-$asset" },
        )

        assertEquals(standing + addedKan + closedKan, cards.map { it.tileId })
        assertEquals(listOf(0, 0) + List(4) { 1 } + List(4) { 2 }, cards.map { it.group })
        val backs = cards.filter { it.backAppearanceId != null }
        assertEquals(listOf(closedKan.first(), closedKan.last()), backs.map { it.tileId })
        assertEquals(backs.map { "back-of-asset-${it.tileId}" }, backs.map { it.backAppearanceId })
        assertEquals(List(2) { UNKNOWN_TILE_ASSET_KEY }, backs.map { it.assetKey })
        val faces = cards.filter { it.backAppearanceId == null }
        assertEquals(faces.map { "asset-${it.tileId}" }, faces.map { it.assetKey })
    }

    /** 取不到牌面素材的牌不列入。 */
    @Test
    fun `skips tiles without an asset`() {
        val standing = List(3) { Uuid.random() }

        val cards = showcaseWingCards(
            standingTileIds = standing,
            melds = emptyList(),
            assetKeyOf = { id -> if (id == standing[1]) null else "m1" },
            backAppearanceOf = { error("No tile is face down") },
        )

        assertEquals(listOf(standing[0], standing[2]), cards.map { it.tileId })
    }
}
