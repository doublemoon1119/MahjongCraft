package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 針對 [RiichiWallFactory] 進行的單元測試。
 *
 * 驗證日本麻將牌山的組成，特別是赤牌（Red Dora）在不同配置下的數量與分佈。
 */
class RiichiWallFactoryTest {

    /**
     * 驗證日麻牌山總數與標準 3 赤牌配置。
     *
     * 預期總數為 136 張，且萬、筒、條應各有一張赤牌。
     */
    @Test
    fun `test riichi wall composition with three red dora`() {
        val config = RiichiRuleConfig(redDoraCount = 3)
        val factory = RiichiWallFactory(config)
        val wall = factory.create()

        assertEquals(136, wall.remainingCount)

        val allTiles = wall.getAllTiles()
        val redCount = allTiles.count { (it.tile as? Tile.Numeric)?.isRed == true }
        assertEquals(3, redCount)

        val redBySuit = allTiles.filter { (it.tile as? Tile.Numeric)?.isRed == true }
            .groupBy { (it.tile as Tile.Numeric).suit }

        redBySuit.forEach { (_, tiles) ->
            assertEquals(1, tiles.size)
        }
    }

    /**
     * 驗證 4 赤牌配置下，筒子應佔有 2 張赤牌。
     */
    @Test
    fun `test riichi wall composition with four red dora`() {
        val config = RiichiRuleConfig(redDoraCount = 4)
        val factory = RiichiWallFactory(config)
        val wall = factory.create()

        val allTiles = wall.getAllTiles()
        val redBySuit = allTiles.filter { (it.tile as? Tile.Numeric)?.isRed == true }
            .groupBy { (it.tile as Tile.Numeric).suit }

        // 驗證筒子 (Dot) 應有兩張赤牌
        assertEquals(2, redBySuit[Tile.Suit.Dot]?.size)
    }
}