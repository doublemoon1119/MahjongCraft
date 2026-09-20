package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiTileOrder
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證副露呈現資料的牌序與鳴取牌辨識。 */
class MeldPresentationTest {
    private val four = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 4))
    private val two = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 2))
    private val three = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 3))

    /** 鳴牌當下鳴取的那張排在最後，呈現資料依牌序重排，鳴取牌改由 ID 指出。 */
    @Test
    fun `sorts the tiles by the rule order`() {
        val meld = Meld(
            type = MeldType.CHI,
            tiles = listOf(two, four, three),
            sourceTile = three,
            sourceDirection = RelativeDirection.Left,
        )

        val presentation = meld.toPresentation(revealsClosedKanTiles = true, tileOrder = RiichiTileOrder)

        assertEquals(listOf(two.id, three.id, four.id), presentation.tileIds)
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
        )

        val presentation = meld.toPresentation(revealsClosedKanTiles = true, tileOrder = RiichiTileOrder)

        assertEquals(added.id, presentation.tileIds.last())
        assertTrue(presentation.tileIds.dropLast(1).containsAll(pon.map { it.id }))
    }
}
