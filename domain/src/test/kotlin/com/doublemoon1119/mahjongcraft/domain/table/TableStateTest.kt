package com.doublemoon1119.mahjongcraft.domain.table

import com.doublemoon1119.mahjongcraft.domain.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.domain.fakes.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.domain.fakes.FakeMahjongRuleConfig
import com.doublemoon1119.mahjongcraft.domain.fakes.FakeScoreConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 測試用的模擬動態規則狀態實作。
 */
private class MockDynamicState : DynamicRuleState

/**
 * 針對 [TableState] 的基礎通用邏輯進行單元測試。
 *
 * 驗證包含玩家分數初始化、下家邏輯判定以及動態狀態持有等核心功能。
 */
class TableStateTest {

    /**
     * 驗證當 [TableState] 初始化時，是否能正確根據規則配置設定所有玩家的初始分數。
     */
    @Test
    fun `test player score initialization from config`() {
        val initialScoreValue = 30000
        // 透過傳入具備特定分數的 FakeScoreConfig 來達成測試需求
        val config = FakeMahjongRuleConfig(
            scoreConfig = FakeScoreConfig(initialScore = initialScoreValue)
        )
        val players = listOf(
            FakeMahjongPlayerFactory.create(name = "P1", initialSeat = Wind.EAST),
            FakeMahjongPlayerFactory.create(name = "P2", initialSeat = Wind.SOUTH)
        )

        TableState(
            players = players,
            tileWall = TileWall(mutableListOf()),
            config = config
        )

        for (player in players) {
            assertEquals(
                initialScoreValue,
                player.score,
                "Player score should be initialized to the value defined in config."
            )
        }
    }

    /**
     * 驗證下家獲取邏輯是否正確，並確保支援動態人數（如三人麻將）。
     */
    @Test
    fun `test next player logic supports dynamic player count`() {
        val p1 = FakeMahjongPlayerFactory.create("Player 1", Wind.EAST)
        val p2 = FakeMahjongPlayerFactory.create("Player 2", Wind.SOUTH)
        val p3 = FakeMahjongPlayerFactory.create("Player 3", Wind.WEST)

        val table = TableState(
            players = listOf(p1, p2, p3),
            tileWall = TileWall(mutableListOf()),
            config = FakeMahjongRuleConfig()
        )

        assertEquals(3, table.playerCount, "Table should correctly reflect the number of joined players.")
        assertEquals(p2, table.getNextPlayer(p1), "The next player of P1 (East) should be P2 (South).")
        assertEquals(
            p1,
            table.getNextPlayer(p3),
            "The next player of the last person (P3) should wrap back to the first person (P1)."
        )
    }

    /**
     * 驗證 TableState 能正確持有並透過介面存取動態規則狀態。
     */
    @Test
    fun `test dynamic rule state assignment`() {
        val dynamicState = MockDynamicState()
        val table = TableState(
            players = emptyList(),
            tileWall = TileWall(mutableListOf()),
            config = FakeMahjongRuleConfig(),
            dynamicRuleState = dynamicState
        )

        assertEquals(dynamicState, table.dynamicRuleState, "TableState should hold the assigned dynamic rule state.")
    }
}