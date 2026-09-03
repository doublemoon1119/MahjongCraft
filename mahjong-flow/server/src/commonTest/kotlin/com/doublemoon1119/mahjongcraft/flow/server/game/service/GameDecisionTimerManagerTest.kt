package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
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

    /**
     * 驗證玩家在基本思考時間到一半時退出兩人麻將進金重進後（比照新 server session），新計時器接續中斷時剩餘的
     * 基本思考時間，而不是重新取得完整基本思考時間。
     *
     * 重現實際 bug：玩家輪到自己行動時退出單人遊戲後重進，此前 `baseDurationMillis` 倒數會重新跑完整基本思考時間，
     * 而不是接續 `interruptedBaseMillisByPlayerId` 剩餘量。`settleAll()` 比照真實 server 停止流程寫回中斷基本思考時間，
     * 另建一個不共享 runtime timer 的新 manager 實例比照全新 session。
     */
    @Test
    fun `test session restart resumes interrupted base time instead of granting a fresh one`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val game = fixtures.ownTurnGame(playerId)
        fixtures.repository.setGame(game)
        fixtures.manager.reconcile(game.id)
        fixtures.clock.nowMillis = 1_500L

        fixtures.manager.settleAll()

        val settledGame = fixtures.repository.getGame(game.id)!!
        assertEquals(3_500L, settledGame.interruptedBaseMillisByPlayerId.getValue(playerId))
        assertEquals(20_000L, settledGame.remainingReserveMillisByPlayerId.getValue(playerId))
        assertTrue(settledGame.forcedAutoPlayPlayerIds.isEmpty())

        // 比照全新 session：共用同一 repository 與 clock，但是全新的 manager 實例，不繼承任何既有 runtime timer。
        val restartedManager = GameDecisionTimerManager(
            gameRepository = fixtures.repository,
            authorityResolver = GameDecisionAuthorityResolver(),
            timerFactory = PlayerDecisionTimerFactory(fixtures.clock),
            clock = fixtures.clock,
        )

        val status = restartedManager.reconcile(game.id).getValue(playerId)

        assertEquals(3_500L, status.time.baseRemainingMillis)
        assertEquals(20_000L, status.time.reserveRemainingMillis)
        assertTrue(
            fixtures.repository.getGame(game.id)!!.interruptedBaseMillisByPlayerId.isEmpty(),
            "the interrupted base time must be consumed once resumed, not left around for reuse",
        )
    }

    /** 驗證已耗盡全部思考時間的決策在 settle 時不會留下可接續的基本思考時間，而是直接進入強制自動操作。 */
    @Test
    fun `test settle all does not resume a fully timed out decision`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val game = fixtures.ownTurnGame(playerId)
        fixtures.repository.setGame(game)
        fixtures.manager.reconcile(game.id)
        fixtures.clock.nowMillis = 26_000L

        fixtures.manager.settleAll()

        val settledGame = fixtures.repository.getGame(game.id)!!
        assertTrue(settledGame.interruptedBaseMillisByPlayerId.isEmpty())
        assertEquals(0L, settledGame.remainingReserveMillisByPlayerId.getValue(playerId))
        assertEquals(setOf(playerId), settledGame.forcedAutoPlayPlayerIds)
    }

    /** 驗證中斷基本思考時間只接續一次：重建後再次退出重進不會重複套用同一筆中斷資料。 */
    @Test
    fun `test resumed base time is not reused across multiple restarts`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val game = fixtures.ownTurnGame(playerId)
        fixtures.repository.setGame(game)
        fixtures.manager.reconcile(game.id)
        fixtures.clock.nowMillis = 1_500L
        fixtures.manager.settleAll()

        val firstRestart = GameDecisionTimerManager(
            gameRepository = fixtures.repository,
            authorityResolver = GameDecisionAuthorityResolver(),
            timerFactory = PlayerDecisionTimerFactory(fixtures.clock),
            clock = fixtures.clock,
        )
        firstRestart.reconcile(game.id)
        fixtures.clock.nowMillis = 2_500L
        firstRestart.settleAll()

        val reSettledGame = fixtures.repository.getGame(game.id)!!
        // 第一次重連後已接續過一次（剩 3_500ms），這次又過 1_000ms，應留 2_500ms、不是回到原始 3_500ms。
        assertEquals(2_500L, reSettledGame.interruptedBaseMillisByPlayerId.getValue(playerId))

        val secondRestart = GameDecisionTimerManager(
            gameRepository = fixtures.repository,
            authorityResolver = GameDecisionAuthorityResolver(),
            timerFactory = PlayerDecisionTimerFactory(fixtures.clock),
            clock = fixtures.clock,
        )
        val status = secondRestart.reconcile(game.id).getValue(playerId)

        assertEquals(2_500L, status.time.baseRemainingMillis)
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

    /** 驗證完整逾時只會被取得一次，並持久標記玩家進入強制自動操作。 */
    @Test
    fun `test timed out decision activates forced auto play once`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val game = fixtures.reactionGame(playerId)
        fixtures.repository.setGame(game)
        fixtures.manager.reconcile(game.id)
        fixtures.clock.nowMillis = 25_000L

        val claimed = fixtures.manager.claimTimedOutDecisions()

        assertEquals(
            listOf(TimedOutPlayerDecision(game.id, playerId, PlayerDecisionPhase.DISCARD_REACTION)),
            claimed,
        )
        val updatedGame = fixtures.repository.getGame(game.id)!!
        assertEquals(0L, updatedGame.remainingReserveMillisByPlayerId.getValue(playerId))
        assertEquals(setOf(playerId), updatedGame.forcedAutoPlayPlayerIds)
        assertTrue(fixtures.manager.claimTimedOutDecisions().isEmpty())
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

        /** 建立單一玩家剛摸到牌、輪到自己一般回合的遊戲，比照使用者實際碰到的情境。 */
        fun ownTurnGame(playerId: Uuid): Game {
            val player = FakeMahjongPlayerFactory.create(
                id = playerId,
                hand = FakeHandFactory.create(lastDrawn = Tile.Honor.East),
            )
            return Game(
                tableState = FakeTableStateFactory.create(players = listOf(player)),
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
