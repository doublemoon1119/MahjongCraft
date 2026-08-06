package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 針對 [GameInitializer] 進行單元測試。
 *
 * 驗證開局時座位分配、初始手牌張數、牌山剩餘數量與初始分數是否正確。
 */
class GameInitializerTest {

    private val module = RiichiRuleModule(id = "mahjongcraft:riichi", config = RiichiRuleConfig())

    /**
     * 驗證每位玩家皆被分配到唯一的座位，且座位數量與玩家人數一致。
     */
    @Test
    fun `test initialize assigns one unique seat per player`() {
        val playerIds = List(4) { Uuid.random() }
        val gameId = Uuid.random()

        val table = GameInitializer.initialize(gameId, playerIds, module)

        assertEquals(4, table.players.size)
        assertEquals(setOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH), table.players.map { it.initialSeat }.toSet())
        assertEquals(playerIds.toSet(), table.players.map { it.id }.toSet())
    }

    /**
     * 驗證每位玩家的初始手牌張數符合規則配置。
     */
    @Test
    fun `test initialize deals initial hand size per rule config`() {
        val playerIds = List(4) { Uuid.random() }
        val table = GameInitializer.initialize(Uuid.random(), playerIds, module)

        table.players.forEach { player ->
            assertEquals(module.config.initialHandSize, player.hand.tiles.size)
        }
    }

    /**
     * 驗證發牌後牌山剩餘數量等於總牌數扣除所有玩家已發出的手牌。
     */
    @Test
    fun `test initialize leaves correct remaining wall count`() {
        val playerIds = List(4) { Uuid.random() }
        val totalTileCount = module.createWallFactory().create().remainingCount
        val table = GameInitializer.initialize(Uuid.random(), playerIds, module)

        val expectedRemaining = totalTileCount - playerIds.size * module.config.initialHandSize
        assertEquals(expectedRemaining, table.tileWall.remainingCount)
    }

    /**
     * 驗證 [TableState.init] 已在開局時套用，所有玩家分數皆為規則配置的初始分數。
     */
    @Test
    fun `test initialize sets initial score from config`() {
        val playerIds = List(4) { Uuid.random() }
        val table = GameInitializer.initialize(Uuid.random(), playerIds, module)

        table.players.forEach { player ->
            assertEquals(module.config.scoreConfig.initialScore, player.score)
        }
    }

    /**
     * 驗證座位分配確實隨機，而不是每次都固定把 EAST 分配給玩家列表的第一位。
     */
    @Test
    fun `test initialize randomizes seat assignment across calls`() {
        val playerIds = List(4) { Uuid.random() }

        val firstPlayerSeats = List(20) {
            GameInitializer.initialize(Uuid.random(), playerIds, module)
                .players.first { it.id == playerIds[0] }.initialSeat
        }

        assertTrue(firstPlayerSeats.toSet().size > 1, "Seat assignment should vary across multiple initializations.")
    }

    /**
     * 驗證玩家人數不在規則允許範圍內時會拋出例外。
     */
    @Test
    fun `test initialize throws when player count out of range`() {
        val tooFewPlayerIds = listOf(Uuid.random())

        assertFailsWith<IllegalArgumentException> {
            GameInitializer.initialize(Uuid.random(), tooFewPlayerIds, module)
        }
    }
}
