package com.doublemoon1119.mahjongcraft.logic.rules.riichi.layout

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 驗證四人日本麻將固定 136 張的牌牆布局。 */
class RiichiWallLayoutTest {

    /** 建立 136 張互不相同（依 [IdentifiedTile.id] 區分）的測試牌，牌面本身無關緊要。 */
    private fun buildShuffledTiles(): List<IdentifiedTile> = List(136) {
        FakeIdentifiedTileFactory.create(Tile.Honor.East)
    }

    /** 驗證活牌 122 張、王牌 14 張，且兩者合計涵蓋全部輸入牌、沒有重複或遺漏。 */
    @Test
    fun `resolve splits the wall into 122 live tiles and 14 dead wall tiles covering every input tile`() {
        val tiles = buildShuffledTiles()
        val result = RiichiWallLayout.resolve(tiles, WallOpening(wallSideOffsetFromDealer = 3, stacksFromRight = 8))

        assertEquals(122, result.drawOrder.size)
        assertEquals(14, result.initialDeadWall.size)
        assertEquals(tiles.map { it.id }.toSet(), (result.drawOrder + result.initialDeadWall).map { it.id }.toSet())
        assertEquals(136, (result.drawOrder + result.initialDeadWall).map { it.id }.toSet().size)
    }

    /** 驗證 structure 涵蓋全部 136 張牌，且面／墩／層座標範圍合法、彼此不重複。 */
    @Test
    fun `structure assigns a unique valid position to every tile`() {
        val tiles = buildShuffledTiles()
        val result = RiichiWallLayout.resolve(tiles, WallOpening(wallSideOffsetFromDealer = 0, stacksFromRight = 1))

        assertEquals(136, result.structure.size)
        assertEquals(tiles.map { it.id }.toSet(), result.structure.keys)
        result.structure.values.forEach { position ->
            assertTrue(position.side in 0..3, "Unexpected side: ${position.side}")
            assertTrue(position.stack in 0..16, "Unexpected stack: ${position.stack}")
            assertTrue(position.layer in 0..1, "Unexpected layer: ${position.layer}")
        }
        assertEquals(136, result.structure.values.toSet().size)
    }

    /** 驗證王牌緊鄰開門缺口右側，活牌緊鄰王牌另一端，兩者中間不留空隙也不重疊。 */
    @Test
    fun `dead wall sits immediately to the right of the break and live wall fills the rest`() {
        val tiles = buildShuffledTiles()
        val opening = WallOpening(wallSideOffsetFromDealer = 1, stacksFromRight = 5)
        val result = RiichiWallLayout.resolve(tiles, opening)

        val deadWallPositions = result.initialDeadWall.map { result.structure.getValue(it.id) }.toSet()
        val liveWallPositions = result.drawOrder.map { result.structure.getValue(it.id) }.toSet()

        // 開門缺口右側緊鄰的墩：面 1，從右數第 5 墩（一基底），零基底墩序號 = 5 - 1 = 4。
        val breakStack = TileWallPosition(side = 1, stack = 4, layer = 0)
        assertTrue(deadWallPositions.any { it.side == breakStack.side && it.stack == breakStack.stack })

        assertEquals(7, deadWallPositions.map { it.side to it.stack }.toSet().size)
        assertEquals(61, liveWallPositions.map { it.side to it.stack }.toSet().size)
        assertTrue(deadWallPositions.intersect(liveWallPositions).isEmpty())
    }

    /** 驗證牌數不符時明確拋出例外，不靜默截斷或補牌。 */
    @Test
    fun `resolve rejects a tile list that is not exactly 136 tiles`() {
        assertFailsWith<IllegalArgumentException> {
            RiichiWallLayout.resolve(buildShuffledTiles().drop(1), WallOpening(wallSideOffsetFromDealer = 0, stacksFromRight = 1))
        }
        assertFailsWith<IllegalArgumentException> {
            RiichiWallLayout.resolve(buildShuffledTiles() + buildShuffledTiles().first(), WallOpening(0, 1))
        }
    }

    /** 驗證超出面數或每面墩數範圍的開門結果會被拒絕。 */
    @Test
    fun `resolve rejects an opening outside the wall bounds`() {
        val tiles = buildShuffledTiles()

        assertFailsWith<IllegalArgumentException> {
            RiichiWallLayout.resolve(tiles, WallOpening(wallSideOffsetFromDealer = 4, stacksFromRight = 1))
        }
        assertFailsWith<IllegalArgumentException> {
            RiichiWallLayout.resolve(tiles, WallOpening(wallSideOffsetFromDealer = 0, stacksFromRight = 18))
        }
    }
}
