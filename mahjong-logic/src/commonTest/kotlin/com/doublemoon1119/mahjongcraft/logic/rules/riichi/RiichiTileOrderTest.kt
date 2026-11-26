package com.doublemoon1119.mahjongcraft.logic.rules.riichi
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileComponents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * 針對 [RiichiTileOrder] 進行單元測試。
 */
class RiichiTileOrderTest {
    /**
     * 測試白、中的排序順序。
     */
    @Test
    fun `test Riichi style sorting`() {
        val red = Tile.Honor.Red // 中
        val white = Tile.Honor.White // 白

        val list = listOf(red, white)
        val sorted = list.sortedWith(RiichiTileOrder)

        // 日本麻將：白在前，中在後
        assertEquals(listOf(white, red), sorted)
    }

    /** 普通五與帶赤寶牌組件的五依完整牌值排序，整理手牌時也保留這個順序。 */
    @Test
    fun `full value order places component red five after ordinary five`() {
        val five = Tile.Numeric(Tile.Suit.Dot, 5)
        val ordinary = IdentifiedTile(Uuid.random(), five)
        val red = ordinary.copy(id = Uuid.random(), components = ComponentSet.EMPTY.with(RiichiTileComponents.RED_DORA, Unit))
        val six = IdentifiedTile(Uuid.random(), Tile.Numeric(Tile.Suit.Dot, 6))
        val hand = Hand(tiles = listOf(six, red, ordinary))

        assertEquals(listOf(ordinary.id, red.id, six.id), hand.organize(RiichiTileOrder).tiles.map { it.id })
        assertEquals(0, RiichiTileOrder.compare(five, red.tile))
        assertEquals(-1, RiichiTileOrder.valueOrder.compare(TileValue(five), TileValue(five, red.components)))
    }
}
