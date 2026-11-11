package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionStep
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTimeSource
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** [GameDecisionTimerManager] 的決策保留與剩餘思考時間結算測試。 */
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
        fixtures.clock.now = 8_000.milliseconds
        fixtures.repository.updateGame(game.id) { current ->
            val pending = current!!.tableState.pendingReaction!!
            current.copy(
                tableState = current.tableState.copy(
                    turnPhase = TurnPhase.AwaitingDiscardReaction(pending.copy(responses = mapOf(firstId to GameAction.Pass))),
                ),
            ) to Unit
        }

        val statuses = fixtures.manager.reconcile(game.id, completedPlayerId = firstId)

        assertEquals(setOf(secondId), statuses.keys)
        assertEquals(PlayerDecisionPhase.DISCARD_REACTION, statuses.getValue(secondId).phase)
        assertEquals(0.milliseconds, statuses.getValue(secondId).time.baseRemaining)
        assertEquals(17_000.milliseconds, statuses.getValue(secondId).time.reserveRemaining)
        val updatedGame = fixtures.repository.getGame(game.id)!!
        assertEquals(17_000.milliseconds, updatedGame.remainingReserveByPlayerId.getValue(firstId))
        assertEquals(20_000.milliseconds, updatedGame.remainingReserveByPlayerId.getValue(secondId))
    }

    /** 驗證失去決策權的 timer 會結算保留思考時間 並從 runtime 索引移除。 */
    @Test
    fun `test removed decision settles reserve time`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val game = fixtures.reactionGame(playerId)
        fixtures.repository.setGame(game)
        fixtures.manager.reconcile(game.id)
        fixtures.clock.now = 8_000.milliseconds
        fixtures.repository.updateGame(game.id) { current ->
            current!!.copy(tableState = current.tableState.copy(turnPhase = TurnPhase.AwaitingDraw)) to Unit
        }

        val statuses = fixtures.manager.reconcile(game.id)

        assertTrue(statuses.isEmpty())
        assertEquals(
            17_000.milliseconds,
            fixtures.repository.getGame(game.id)!!.remainingReserveByPlayerId.getValue(playerId),
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
        fixtures.clock.now = 8_000.milliseconds
        fixtures.repository.updateGame(game.id) { current ->
            val currentPlayer = current!!.tableState.currentPlayer
            current.copy(
                tableState = current.tableState.copy(
                    turnPhase = TurnPhase.AwaitingDiscard,
                    players = current.tableState.players.map { player ->
                        if (player.id == playerId) {
                            player.copy(actionHistory = currentPlayer.actionHistory + GameAction.Pon(Uuid.random(), emptyList()))
                        } else {
                            player
                        }
                    },
                ),
            ) to Unit
        }

        val status = fixtures.manager.reconcile(game.id, completedPlayerId = playerId).getValue(playerId)

        assertEquals(PlayerDecisionPhase.OWN_TURN, status.phase)
        assertEquals(5_000.milliseconds, status.time.baseRemaining)
        assertEquals(17_000.milliseconds, status.time.reserveRemaining)
        assertEquals(
            17_000.milliseconds,
            fixtures.repository.getGame(game.id)!!.remainingReserveByPlayerId.getValue(playerId),
        )
    }

    /** 驗證結算計時後重建管理器，會接續已保存的基本思考時間，而非重新給滿；不執行平台存檔或重啟。 */
    @Test
    fun `test session restart resumes interrupted base time instead of granting a fresh one`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val game = fixtures.ownTurnGame(playerId)
        fixtures.repository.setGame(game)
        fixtures.manager.reconcile(game.id)
        fixtures.clock.now = 1_500.milliseconds

        fixtures.manager.settleAll()

        val settledGame = fixtures.repository.getGame(game.id)!!
        assertEquals(3_500.milliseconds, settledGame.interruptedBaseByPlayerId.getValue(playerId))
        assertEquals(20_000.milliseconds, settledGame.remainingReserveByPlayerId.getValue(playerId))
        assertTrue(settledGame.forcedAutoPlayPlayerIds.isEmpty())

        // 比照全新 session：共用同一 repository 與 clock，但是全新的 manager 實例，不繼承任何既有 runtime timer。
        val restartedManager = GameDecisionTimerManager(
            gameRepository = fixtures.repository,
            authorityResolver = GameDecisionAuthorityResolver(),
            timerFactory = PlayerDecisionTimerFactory(fixtures.clock),
            clock = fixtures.clock,
        )

        val status = restartedManager.reconcile(game.id).getValue(playerId)

        assertEquals(3_500.milliseconds, status.time.baseRemaining)
        assertEquals(20_000.milliseconds, status.time.reserveRemaining)
        assertTrue(
            fixtures.repository.getGame(game.id)!!.interruptedBaseByPlayerId.isEmpty(),
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
        fixtures.clock.now = 26_000.milliseconds

        fixtures.manager.settleAll()

        val settledGame = fixtures.repository.getGame(game.id)!!
        assertTrue(settledGame.interruptedBaseByPlayerId.isEmpty())
        assertEquals(0.milliseconds, settledGame.remainingReserveByPlayerId.getValue(playerId))
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
        fixtures.clock.now = 1_500.milliseconds
        fixtures.manager.settleAll()

        val firstRestart = GameDecisionTimerManager(
            gameRepository = fixtures.repository,
            authorityResolver = GameDecisionAuthorityResolver(),
            timerFactory = PlayerDecisionTimerFactory(fixtures.clock),
            clock = fixtures.clock,
        )
        firstRestart.reconcile(game.id)
        fixtures.clock.now = 2_500.milliseconds
        firstRestart.settleAll()

        val reSettledGame = fixtures.repository.getGame(game.id)!!
        // 第一次重連後已接續過一次（剩 3_500ms），這次又過 1_000ms，應留 2_500ms、不是回到原始 3_500ms。
        assertEquals(2_500.milliseconds, reSettledGame.interruptedBaseByPlayerId.getValue(playerId))

        val secondRestart = GameDecisionTimerManager(
            gameRepository = fixtures.repository,
            authorityResolver = GameDecisionAuthorityResolver(),
            timerFactory = PlayerDecisionTimerFactory(fixtures.clock),
            clock = fixtures.clock,
        )
        val status = secondRestart.reconcile(game.id).getValue(playerId)

        assertEquals(2_500.milliseconds, status.time.baseRemaining)
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
        fixtures.clock.now = 8_000.milliseconds

        fixtures.manager.settleAll()

        val updatedGame = fixtures.repository.getGame(game.id)!!
        assertEquals(17_000.milliseconds, updatedGame.remainingReserveByPlayerId.getValue(firstId))
        assertEquals(17_000.milliseconds, updatedGame.remainingReserveByPlayerId.getValue(secondId))
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
        fixtures.clock.now = 25_000.milliseconds

        val claimed = fixtures.manager.claimTimedOutDecisions()

        assertEquals(
            listOf(TimedOutPlayerDecision(game.id, playerId, PlayerDecisionPhase.DISCARD_REACTION)),
            claimed,
        )
        val updatedGame = fixtures.repository.getGame(game.id)!!
        assertEquals(0.milliseconds, updatedGame.remainingReserveByPlayerId.getValue(playerId))
        assertEquals(setOf(playerId), updatedGame.forcedAutoPlayPlayerIds)
        assertTrue(fixtures.manager.claimTimedOutDecisions().isEmpty())
    }

    /**
     * 驗證 [PlayerDecisionPhase.RULE_DECISION] 逾時進入強制自動操作時，不會清空玩家的共用保留
     * 思考時間池——這個階段從未動用過那個池子，逾時只代表規則選擇時間用完，跟其他階段
     * 逾時會把保留思考時間歸零的行為不同。
     */
    @Test
    fun `test rule decision timeout does not clear reserve pool`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val game = fixtures.ruleDecisionGame(playerId)
        fixtures.repository.setGame(game)
        fixtures.manager.reconcile(game.id)
        fixtures.clock.now = game.flowConfig.ruleDecision!!.base + 1_000.milliseconds

        val claimed = fixtures.manager.claimTimedOutDecisions()

        assertEquals(
            listOf(TimedOutPlayerDecision(game.id, playerId, PlayerDecisionPhase.RULE_DECISION)),
            claimed,
        )
        val updatedGame = fixtures.repository.getGame(game.id)!!
        assertEquals(20_000.milliseconds, updatedGame.remainingReserveByPlayerId.getValue(playerId))
        assertEquals(setOf(playerId), updatedGame.forcedAutoPlayPlayerIds)
    }

    /** 比照一般動作計時的規則選擇逾時時，會用完共用保留時間並進入強制自動操作。 */
    @Test
    fun `test rule action timeout clears reserve pool`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val game = fixtures.ruleDecisionGame(playerId, RuleDecisionTimeSource.ACTION_TIME)
        fixtures.repository.setGame(game)
        fixtures.manager.reconcile(game.id)
        fixtures.clock.now = 31_000.milliseconds

        val claimed = fixtures.manager.claimTimedOutDecisions()

        assertEquals(
            listOf(TimedOutPlayerDecision(game.id, playerId, PlayerDecisionPhase.RULE_ACTION)),
            claimed,
        )
        val updatedGame = fixtures.repository.getGame(game.id)!!
        assertEquals(0.milliseconds, updatedGame.remainingReserveByPlayerId.getValue(playerId))
        assertEquals(setOf(playerId), updatedGame.forcedAutoPlayPlayerIds)
    }

    /** 驗證離開規則選擇階段時，不把此階段未使用的保留時間零值寫回玩家共用時間池。 */
    @Test
    fun `test leaving rule decision does not overwrite reserve pool`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val game = fixtures.ruleDecisionGame(playerId)
        fixtures.repository.setGame(game)
        fixtures.manager.reconcile(game.id)
        fixtures.clock.now = 5_000.milliseconds
        fixtures.repository.updateGame(game.id) { current ->
            current!!.copy(pendingRuleDecision = null) to Unit
        }

        val statuses = fixtures.manager.reconcile(game.id)

        assertTrue(statuses.isEmpty())
        assertEquals(
            20_000.milliseconds,
            fixtures.repository.getGame(game.id)!!.remainingReserveByPlayerId.getValue(playerId),
        )
    }

    /** 驗證暫停會保存剩餘時間，且暫停期間經過的單調時間不會被計入決策時間。 */
    @Test
    fun `test pause preserves remaining time until decision resumes`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val game = fixtures.ownTurnGame(playerId)
        fixtures.repository.setGame(game)
        fixtures.manager.reconcile(game.id)
        fixtures.clock.now = 1_500.milliseconds

        fixtures.manager.pause(game.id)

        assertTrue(fixtures.manager.getStatuses(game.id).isEmpty())
        val pausedGame = fixtures.repository.getGame(game.id)!!
        assertEquals(3_500.milliseconds, pausedGame.interruptedBaseByPlayerId.getValue(playerId))
        assertEquals(20_000.milliseconds, pausedGame.remainingReserveByPlayerId.getValue(playerId))

        fixtures.clock.now = 11_500.milliseconds
        val resumed = fixtures.manager.reconcile(game.id).getValue(playerId)
        assertEquals(3_500.milliseconds, resumed.time.baseRemaining)
        assertEquals(20_000.milliseconds, resumed.time.reserveRemaining)

        fixtures.clock.now = 12_500.milliseconds
        assertEquals(2_500.milliseconds, fixtures.manager.getStatuses(game.id).getValue(playerId).time.baseRemaining)
    }

    /** 驗證暫停時已完成決策的玩家，不會把舊決策的基本時間帶進下一次決策。 */
    @Test
    fun `test pause does not preserve completed player decision`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val game = fixtures.ownTurnGame(playerId)
        fixtures.repository.setGame(game)
        fixtures.manager.reconcile(game.id)
        fixtures.clock.now = 1_500.milliseconds

        fixtures.manager.pause(game.id, completedPlayerId = playerId)

        assertTrue(fixtures.repository.getGame(game.id)!!.interruptedBaseByPlayerId.isEmpty())
        val resumed = fixtures.manager.reconcile(game.id).getValue(playerId)
        assertEquals(5_000.milliseconds, resumed.time.baseRemaining)
    }

    /** 驗證規則選擇暫停只保存基本時間，不會覆寫玩家共用的保留思考時間池。 */
    @Test
    fun `test pausing rule decision preserves reserve pool`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val game = fixtures.ruleDecisionGame(playerId)
        fixtures.repository.setGame(game)
        fixtures.manager.reconcile(game.id)
        fixtures.clock.now = 5_000.milliseconds

        fixtures.manager.pause(game.id)

        val pausedGame = fixtures.repository.getGame(game.id)!!
        assertEquals(20_000.milliseconds, pausedGame.remainingReserveByPlayerId.getValue(playerId))
        assertEquals(
            game.flowConfig.ruleDecision!!.base - 5_000.milliseconds,
            pausedGame.interruptedBaseByPlayerId.getValue(playerId),
        )
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

        /**
         * 建立指定合資格玩家的捨牌反應遊戲。
         *
         * @param eligiblePlayerIds 可回應捨牌反應的玩家識別碼。
         * @return 含指定待處理反應的測試遊戲。
         */
        fun reactionGame(vararg eligiblePlayerIds: Uuid): Game {
            val players = eligiblePlayerIds.map { FakeMahjongPlayerFactory.create(id = it) }
            return Game(
                tableState = FakeTableStateFactory.create(
                    players = players,
                    pendingReaction = PendingReaction(
                        discarderId = Uuid.random(),
                        tileId = Uuid.random(),
                        eligiblePlayerIds = eligiblePlayerIds.toSet(),
                        ronPlayerIds = eligiblePlayerIds.toSet(),
                        id = Uuid.random(),
                    ),
                ),
                flowConfig = GameFlowConfig(),
            )
        }

        /**
         * 建立單一玩家剛摸到牌、輪到自己一般回合的遊戲。
         *
         * @param playerId 玩家識別碼。
         * @return 該玩家持有摸牌且輪到自己回合的測試遊戲。
         */
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

        /**
         * 建立單一玩家正在 [PlayerDecisionPhase.RULE_DECISION] 的遊戲。
         *
         * @param playerId 玩家識別碼。
         * @return 該玩家等待局前確認的測試遊戲。
         */
        fun ruleDecisionGame(
            playerId: Uuid,
            timeSource: RuleDecisionTimeSource = RuleDecisionTimeSource.RULE_DECISION_TIME,
        ): Game {
            val player = FakeMahjongPlayerFactory.create(id = playerId)
            return Game(
                tableState = FakeTableStateFactory.create(players = listOf(player)),
                flowConfig = GameFlowConfig(ruleDecision = RuleDecisionConfig(30.seconds)),
                pendingRuleDecision = PendingRuleDecision.open(
                    step = RuleDecisionStep(
                        stepId = "test:step",
                        stepIndex = 0,
                        inputSpecsByPlayerId = mapOf(playerId to RuleDecisionInputSpec.Confirmation),
                        timeSource = timeSource,
                    ),
                    timing = RuleDecisionTiming.ROUND_START,
                ),
            )
        }
    }
}

/** 可由測試直接推進的單調時間來源。 */
private class MutableMonotonicClock : MonotonicClock {
    /** 目前回傳的單調時間毫秒數。 */
    var now: Duration = Duration.ZERO

    /** 回傳目前測試指定的單調時間。 */
    override fun now(): Duration = now
}
