package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** [Game] 的流程 runtime 狀態測試。 */
class GameTest {
    /** 建立遊戲時應依設定為所有玩家初始化剩餘保留思考時間。 */
    @Test
    fun `game initializes reserve time for every player`() {
        val playerIds = listOf(Uuid.random(), Uuid.random())
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = playerIds.map { FakeMahjongPlayerFactory.create(id = it) },
            ),
            flowConfig = GameFlowConfig(timeControl = ActionTimeControl.Custom(baseSeconds = 5, reserveSeconds = 37)),
        )

        assertEquals(playerIds.associateWith { 37_000L }, game.remainingReserveMillisByPlayerId)
    }

    /** 剩餘保留思考時間必須完整對應目前遊戲的玩家集合。 */
    @Test
    fun `game rejects incomplete reserve time state`() {
        val playerId = Uuid.random()
        val tableState = FakeTableStateFactory.create(
            players = listOf(FakeMahjongPlayerFactory.create(id = playerId)),
        )

        assertFailsWith<IllegalArgumentException> {
            Game(tableState, GameFlowConfig(), remainingReserveMillisByPlayerId = emptyMap())
        }
    }

    /** 剩餘保留思考時間不得為負數。 */
    @Test
    fun `game rejects negative reserve time`() {
        val playerId = Uuid.random()
        val tableState = FakeTableStateFactory.create(
            players = listOf(FakeMahjongPlayerFactory.create(id = playerId)),
        )

        assertFailsWith<IllegalArgumentException> {
            Game(tableState, GameFlowConfig(), remainingReserveMillisByPlayerId = mapOf(playerId to -1L))
        }
    }
}
