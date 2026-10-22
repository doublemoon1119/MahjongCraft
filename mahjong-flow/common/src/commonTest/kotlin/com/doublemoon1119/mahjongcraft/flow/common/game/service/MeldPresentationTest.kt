package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證副露呈現資料的牌序與鳴取牌辨識。 */
class MeldPresentationTest {
    private val four = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 4))
    private val two = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 2))
    private val three = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 3))

    /** 鳴牌成立時的牌序保持穩定，鳴取牌改由 ID 指出。 */
    @Test
    fun `preserves meld tile order`() {
        val meld = Meld(
            type = MeldType.CHI,
            tiles = listOf(two, four, three),
            sourceTile = three,
            sourceDirection = RelativeDirection.Left,
            publiclyRevealedTileIds = setOf(two.id, three.id, four.id),
            faceDownTileIds = emptySet(),
        )

        val presentation = meld.toPresentation(revealsClosedKanTiles = true)

        assertEquals(listOf(two.id, four.id, three.id), presentation.tileIds)
        assertEquals(three.id, presentation.calledTileId)
    }

    /** 加槓補上的第四張維持在最後，呈現層才找得到要疊在鳴取牌上的那張。 */
    @Test
    fun `keeps the added kan tile last`() {
        val pon = List(3) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5)) }
        val added = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5))
        val meld = Meld(
            type = MeldType.ADDED_KAN,
            tiles = pon + added,
            sourceTile = pon.first(),
            sourceDirection = RelativeDirection.Across,
            publiclyRevealedTileIds = (pon + added).map { it.id }.toSet(),
            faceDownTileIds = emptySet(),
        )

        val presentation = meld.toPresentation(revealsClosedKanTiles = true)

        assertEquals(added.id, presentation.tileIds.last())
        assertTrue(presentation.tileIds.dropLast(1).containsAll(pon.map { it.id }))
    }
}
