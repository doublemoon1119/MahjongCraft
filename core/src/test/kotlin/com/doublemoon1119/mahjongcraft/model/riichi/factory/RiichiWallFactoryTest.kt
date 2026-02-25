package com.doublemoon1119.mahjongcraft.model.riichi.factory

import com.doublemoon1119.mahjongcraft.model.base.Tile
import com.doublemoon1119.mahjongcraft.model.config.GameLength
import com.doublemoon1119.mahjongcraft.model.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.model.riichi.RiichiScoreConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 針對 [RiichiWallFactory] 進行單元測試。
 */
class RiichiWallFactoryTest {

    /**
     * 模擬日麻配置實作類別。
     */
    private class RiichiMock(override val redDoraCount: Int) : RiichiRuleConfig {
        override val allowOpenTanyao = true
        override val useLocalYaku = false
        override val initialHandSize = 13
        override val tileSet = emptyList<Tile>()
        override val deadTileCount = 14
        override val minimumWinConstraint = 1
        override val scoreConfig = RiichiScoreConfig()
        override val gameLength = object : GameLength {
            override val totalRounds = 8
            override val name = "HALF_CHAN"
        }
    }

    /**
     * 驗證日麻牌山總數與標準 3 赤牌配置。
     */
    @Test
    fun `test riichi wall composition with three red dora`() {
        val config = RiichiMock(redDoraCount = 3)
        val factory = RiichiWallFactory(config)
        val wall = factory.create()

        assertEquals(136, wall.remainingCount)

        val allTiles = wall.getAllTiles()

        // 驗證赤牌總數 (應為 3: 萬1, 筒1, 條1)
        val redCount = allTiles.count { (it.tile as? Tile.Numeric)?.isRed == true }
        assertEquals(3, redCount)

        // 驗證各花色的赤牌分配
        val redBySuit = allTiles.filter { (it.tile as? Tile.Numeric)?.isRed == true }
            .groupBy { (it.tile as Tile.Numeric).suit }

        redBySuit.forEach { (_, tiles) ->
            assertEquals(1, tiles.size, "Each suit should have exactly 1 red dora when total is 3")
        }
    }

    /**
     * 驗證 4 赤牌配置下，筒子應佔有 2 張赤牌。
     */
    @Test
    fun `test riichi wall composition with four red dora`() {
        val config = RiichiMock(redDoraCount = 4)
        val factory = RiichiWallFactory(config)
        val allTiles = factory.create().getAllTiles()

        val redCount = allTiles.count { (it.tile as? Tile.Numeric)?.isRed == true }
        assertEquals(4, redCount)

        val dotRedCount = allTiles.count {
            val tile = it.tile
            tile is Tile.Numeric && tile.suit == Tile.Suit.Dot && tile.isRed
        }
        assertEquals(2, dotRedCount, "Dots should have 2 red dora when total is 4")
    }
}