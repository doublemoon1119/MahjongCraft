package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
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

    /** 本局自動操作狀態接受遊戲玩家與合法 namespaced control ID。 */
    @Test
    fun `game accepts round automatic controls for game players`() {
        val playerId = Uuid.random()
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(FakeMahjongPlayerFactory.create(id = playerId)),
            ),
            flowConfig = GameFlowConfig(),
            enabledAutomaticControlIdsByPlayerId = mapOf(playerId to setOf("example:auto_action")),
        )

        assertEquals(setOf("example:auto_action"), game.enabledAutomaticControlIdsByPlayerId.getValue(playerId))
    }

    /** 本局自動操作狀態不得索引遊戲以外的玩家。 */
    @Test
    fun `game rejects automatic controls for unknown players`() {
        val tableState = FakeTableStateFactory.create()

        assertFailsWith<IllegalArgumentException> {
            Game(
                tableState = tableState,
                flowConfig = GameFlowConfig(),
                enabledAutomaticControlIdsByPlayerId = mapOf(Uuid.random() to setOf("example:auto_action")),
            )
        }
    }

    /** 本局自動操作狀態拒絕無 namespace 的不穩定 ID。 */
    @Test
    fun `game rejects automatic controls without namespace`() {
        val playerId = Uuid.random()
        val tableState = FakeTableStateFactory.create(
            players = listOf(FakeMahjongPlayerFactory.create(id = playerId)),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            Game(
                tableState = tableState,
                flowConfig = GameFlowConfig(),
                enabledAutomaticControlIdsByPlayerId = mapOf(playerId to setOf("auto_action")),
            )
        }

        assertTrue(error.message.orEmpty().contains("namespaced"))
    }
}
