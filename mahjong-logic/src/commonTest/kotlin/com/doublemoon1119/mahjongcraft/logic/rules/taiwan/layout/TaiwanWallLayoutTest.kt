package com.doublemoon1119.mahjongcraft.logic.rules.taiwan.layout

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 驗證四人台灣麻將支援 136（不含花牌）或 144（含花牌）張的牌牆布局。 */
class TaiwanWallLayoutTest {

    /** 使用預設 [TaiwanRuleConfig]（王牌 16 張）的布局實例。 */
    private val layout = TaiwanWallLayout(TaiwanRuleConfig())

    /** 建立指定張數、互不相同（依 [IdentifiedTile.id] 區分）的測試牌，牌面本身無關緊要。 */
    private fun buildShuffledTiles(count: Int): List<IdentifiedTile> = List(count) {
        FakeIdentifiedTileFactory.create(Tile.Honor.East)
    }

    /** 驗證不含花牌時（136 張、每面 17 墩），活牌 120 張、王牌 16 張，涵蓋全部輸入牌。 */
    @Test
    fun `resolve splits 136 tiles into 120 live tiles and 16 dead wall tiles`() {
        val tiles = buildShuffledTiles(136)
        val result = layout.resolve(tiles, WallOpening(wallSideOffsetFromDealer = 2, stacksFromRight = 11))

        assertEquals(120, result.drawOrder.size)
        assertEquals(16, result.initialDeadWall.size)
        assertEquals(tiles.map { it.id }.toSet(), (result.drawOrder + result.initialDeadWall).map { it.id }.toSet())
    }

    /** 驗證含花牌時（144 張、每面 18 墩），活牌 128 張、王牌 16 張，涵蓋全部輸入牌。 */
    @Test
    fun `resolve splits 144 tiles into 128 live tiles and 16 dead wall tiles`() {
        val tiles = buildShuffledTiles(144)
        val result = layout.resolve(tiles, WallOpening(wallSideOffsetFromDealer = 1, stacksFromRight = 18))

        assertEquals(128, result.drawOrder.size)
        assertEquals(16, result.initialDeadWall.size)
        assertEquals(tiles.map { it.id }.toSet(), (result.drawOrder + result.initialDeadWall).map { it.id }.toSet())
    }

    /** 驗證 structure 涵蓋全部輸入牌，且面／墩／層座標範圍隨牌數（含花牌與否）正確調整。 */
    @Test
    fun `structure assigns a unique valid position to every tile for both tile counts`() {
        val withoutFlowers = layout.resolve(buildShuffledTiles(136), WallOpening(0, 1))
        assertEquals(136, withoutFlowers.structure.size)
        withoutFlowers.structure.values.forEach { assertTrue(it.stack in 0..16) }

        val withFlowers = layout.resolve(buildShuffledTiles(144), WallOpening(0, 1))
        assertEquals(144, withFlowers.structure.size)
        withFlowers.structure.values.forEach { assertTrue(it.stack in 0..17) }
    }

    /** 驗證牌數不符 136 或 144 時明確拋出例外，不靜默截斷或補牌。 */
    @Test
    fun `resolve rejects a tile list that is neither 136 nor 144 tiles`() {
        assertFailsWith<IllegalArgumentException> {
            layout.resolve(buildShuffledTiles(135), WallOpening(0, 1))
        }
        assertFailsWith<IllegalArgumentException> {
            layout.resolve(buildShuffledTiles(140), WallOpening(0, 1))
        }
    }

    /** 驗證不含花牌（每面僅 17 墩）時，三骰可能算出的第 18 墩開門結果會被拒絕，不悄悄截斷成第 17 墩。 */
    @Test
    fun `resolve rejects an opening beyond 17 stacks per side when flowers are unused`() {
        assertFailsWith<IllegalArgumentException> {
            layout.resolve(buildShuffledTiles(136), WallOpening(wallSideOffsetFromDealer = 1, stacksFromRight = 18))
        }
    }
}
