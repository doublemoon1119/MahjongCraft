package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [GameDecisionTimerManager] 的決策保留與 B 結算測試。 */
class GameDecisionTimerManagerTest {
    /** 驗證一位玩家回應時保留其他 reaction timer，並只結算已完成玩家的保留思考時間。 */
    @Test
    fun `test completed reaction preserves other player timer`() = runTest {
        val fixtures = Fixtures()
        val firstId = Uuid.random()
        val secondId = Uuid.random()
        val game = fixtures.reactionGame(firstId, secondId)
        fixtures.repository.setGame(game)
        fixtures.manager.reconcile(game.id)
        fixtures.clock.nowMillis = 8_000L
        fixtures.repository.updateGame(game.id) { current ->
            val pending = current!!.tableState.pendingReaction!!
            current.copy(
                tableState = current.tableState.copy(
                    pendingReaction = pending.copy(responses = mapOf(firstId to GameAction.Pass)),
                ),
            ) to Unit
        }

        val statuses = fixtures.manager.reconcile(game.id, completedPlayerId = firstId)

        assertEquals(setOf(secondId), statuses.keys)
        assertEquals(PlayerDecisionPhase.DISCARD_REACTION, statuses.getValue(secondId).phase)
        assertEquals(0L, statuses.getValue(secondId).time.baseRemainingMillis)
        assertEquals(17_000L, statuses.getValue(secondId).time.reserveRemainingMillis)
        val updatedGame = fixtures.repository.getGame(game.id)!!
        assertEquals(17_000L, updatedGame.remainingReserveMillisByPlayerId.getValue(firstId))
        assertEquals(20_000L, updatedGame.remainingReserveMillisByPlayerId.getValue(secondId))
    }

    /** 驗證失去決策權的 timer 會結算保留思考時間 並從 runtime 索引移除。 */
    @Test
    fun `test removed decision settles reserve time`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val game = fixtures.reactionGame(playerId)
        fixtures.repository.setGame(game)
        fixtures.manager.reconcile(game.id)
        fixtures.clock.nowMillis = 8_000L
        fixtures.repository.updateGame(game.id) { current ->
            current!!.copy(tableState = current.tableState.copy(pendingReaction = null)) to Unit
        }

        val statuses = fixtures.manager.reconcile(game.id)

        assertTrue(statuses.isEmpty())
        assertEquals(
            17_000L,
            fixtures.repository.getGame(game.id)!!.remainingReserveMillisByPlayerId.getValue(playerId),
        )
    }

    /** 驗證同一玩家完成舊決策後再次取得決策權會重新取得基本思考時間 並保留已扣除的保留思考時間。 */
    @Test
    fun `test completed player receives fresh action time for next decision`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val game = fixtures.reactionGame(playerId)
        fixtures.repository.setGame(game)
        fixtures.manager.reconcile(game.id)
        fixtures.clock.nowMillis = 8_000L
        fixtures.repository.updateGame(game.id) { current ->
            val currentPlayer = current!!.tableState.currentPlayer
            current.copy(
                tableState = current.tableState.copy(
                    pendingReaction = null,
                    players = current.tableState.players.map { player ->
                        if (player.id == playerId) {
                            player.copy(actionHistory = currentPlayer.actionHistory + GameAction.Pon(Uuid.random()))
                        } else {
                            player
                        }
                    },
                ),
            ) to Unit
        }

        val status = fixtures.manager.reconcile(game.id, completedPlayerId = playerId).getValue(playerId)

        assertEquals(PlayerDecisionPhase.OWN_TURN, status.phase)
        assertEquals(5_000L, status.time.baseRemainingMillis)
        assertEquals(17_000L, status.time.reserveRemainingMillis)
        assertEquals(
            17_000L,
            fixtures.repository.getGame(game.id)!!.remainingReserveMillisByPlayerId.getValue(playerId),
        )
    }

    /** 驗證 session 停止時一次結算所有有效 timer 並清除 runtime 狀態。 */
    @Test
    fun `test settle all persists every active reserve and clears timers`() = runTest {
        val fixtures = Fixtures()
        val firstId = Uuid.random()
        val secondId = Uuid.random()
        val game = fixtures.reactionGame(firstId, secondId)
        fixtures.repository.setGame(game)
        fixtures.manager.reconcile(game.id)
        fixtures.clock.nowMillis = 8_000L

        fixtures.manager.settleAll()

        val updatedGame = fixtures.repository.getGame(game.id)!!
        assertEquals(17_000L, updatedGame.remainingReserveMillisByPlayerId.getValue(firstId))
        assertEquals(17_000L, updatedGame.remainingReserveMillisByPlayerId.getValue(secondId))
        assertTrue(fixtures.manager.getStatuses(game.id).isEmpty())
    }

    /** 提供可控時間、repository 與 manager 的測試組合。 */
    private class Fixtures {
        /** 測試用權威遊戲倉庫。 */
        val repository = FakeGameRepository()

        /** 測試用可控單調時間來源。 */
        val clock = MutableMonotonicClock()

        /** 受測的決策計時管理器。 */
        val manager = GameDecisionTimerManager(
            gameRepository = repository,
            authorityResolver = GameDecisionAuthorityResolver(),
            timerFactory = PlayerDecisionTimerFactory(clock),
            clock = clock,
        )

        /** 建立指定合資格玩家的捨牌反應遊戲。 */
        fun reactionGame(vararg eligiblePlayerIds: Uuid): Game {
            val players = eligiblePlayerIds.map { FakeMahjongPlayerFactory.create(id = it) }
            return Game(
                tableState = FakeTableStateFactory.create(
                    players = players,
                    pendingReaction = PendingReaction(
                        discarderId = Uuid.random(),
                        tileId = Uuid.random(),
                        eligiblePlayerIds = eligiblePlayerIds.toSet(),
                    ),
                ),
                flowConfig = GameFlowConfig(),
            )
        }
    }
}

/** 可由測試直接推進的單調時間來源。 */
private class MutableMonotonicClock : MonotonicClock {
    /** 目前回傳的單調時間毫秒數。 */
    var nowMillis: Long = 0L

    /** 回傳目前測試指定的單調時間。 */
    override fun nowMillis(): Long = nowMillis
}
