package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyDefinition
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchCompletion
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEndedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettledEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventProjector
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.server.game.simulation.SimulationRuntime
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 整場對局的端對端整合測試：4 位 AI 玩家從開局一路打到整場對局結束（東風戰，4 局 + 可能的連莊），
 * 完全透過 [GameFlowCoordinator] 驅動，不依賴遊戲平台。
 *
 * 目的是驗證編排層本身（[GameFlowCoordinator]/[AiTurnDriver]/連莊過莊自動銜接）撐得住一整場對局，
 * 不會卡住、分數守恆。對局環境使用與 AI 對局模擬共用的 [SimulationRuntime]。
 *
 * 刻意用 [FakeAiStrategy]（見下方）而非 [RandomAiStrategy]：
 * 此測試關心完整對局的流程銜接，而不是策略強弱。固定策略對相同情境給出相同命令，
 * 但初始化的牌山仍是隨機的，不保證每次對局經過相同路徑。
 */
class FullMatchIntegrationTest {
    /**
     * 驗證 4 位 AI 玩家能把整場東風戰（4 局，含可能的連莊）打完，不會卡住、分數守恆。
     *
     * 呼叫 [GameFlowCoordinator.driveAutomatedPlayers] 推進整場，再確認對局正常結束。
     * 若流程超出安全迭代上限而中止，仍不能通過正常完賽與結算次數的斷言。
     */
    @Test
    fun `test four ai players play a full east-only match to completion`() = runTest {
        val runtime = SimulationRuntime(defaultStrategyKey = FakeAiStrategy.KEY).apply {
            aiStrategyRegistry.register(MahjongAiStrategyDefinition(FakeAiStrategy.KEY) { FakeAiStrategy() })
        }
        val gameId = Uuid.random()
        val playerIds = List(4) { Uuid.random() }
        val config = RiichiRuleConfig(gameLength = RiichiGameLength.East)
        val module = runtime.moduleRegistry.getModule(config)
        val initialState = GameInitializer.initialize(
            id = gameId,
            playerIds = playerIds,
            module = module,
        ).tableState
        runtime.gameRepository.setTableState(initialState, playerIds.associateWith { FakeAiStrategy.KEY })

        runtime.coordinator.driveAutomatedPlayers(gameId)
        val finalState = runtime.gameRepository.getTableState(gameId)

        assertTrue(
            finalState!!.roundNumber >= RiichiGameLength.East.totalRounds,
            "The match should have progressed through all ${RiichiGameLength.East.totalRounds} rounds, " +
                "not stalled early (actual roundNumber: ${finalState.roundNumber}).",
        )

        val totalScore = finalState.players.sumOf { it.score }
        assertEquals(
            4 * config.scoreConfig.initialScore,
            totalScore,
            "Points only move between players (nobody declares riichi in this test, so no sticks leave the " +
                "table either); the total should be conserved across the whole match.",
        )

        // 每局各通知一次結算，最後通知一次正常打完的對局結束。
        val events = runtime.gameRepository.committedFacts.flatMap(GameEventProjector(runtime.moduleRegistry)::project)
        val completedRounds = runtime.gameRepository.historyDrafts.count { it.fact is HistoryFact.RoundCompleted }
        assertEquals(List(completedRounds) { RoundSettledEvent::class } + MatchEndedEvent::class, events.map { it::class })
        assertEquals(MatchCompletion.COMPLETED, (events.last() as MatchEndedEvent).completion)
    }

    /**
     * 供整場對局整合測試使用的固定策略：刻意不主動鳴牌（吃/碰/槓/立直/九種九牌），只在自然出現
     * 榮和/自摸機會時才拿，其餘時候單純摸牌後打出剛摸到的牌——這個測試關心的是編排層撐不撐得住
     * 一整場對局，不是驗證每種行牌路徑，維持策略單純、行為可預期比覆蓋率更重要（各種鳴牌/立直/
     * 搶槓路徑已經有各自獨立的單元測試涵蓋）。
     */
    private class FakeAiStrategy : MahjongAiStrategy {
        /** 測試策略的註冊資訊。 */
        companion object {
            /** 此固定策略的測試識別碼。 */
            const val KEY = "test:deterministic"
        }

        override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand = when (context.phase) {
            AiDecisionPhase.RespondingToDiscard ->
                GameCommand.RespondToDiscard(
                    context.legalActions.firstOrNull { it is GameAction.Ron }
                        ?: GameAction.Pass,
                    checkNotNull(context.discardReactionId),
                )

            AiDecisionPhase.RespondingToRobbing ->
                GameCommand.RespondToRobbing(
                    context.legalActions.firstOrNull { it is GameAction.Ron }
                        ?: GameAction.Pass,
                )

            AiDecisionPhase.OwnTurn -> {
                if (context.legalActions.contains(GameAction.Tsumo)) {
                    GameCommand.Tsumo
                } else {
                    val hand = context.snapshot.players.first { it.id == context.selfId }.hand
                    val tileId = hand.lastDrawn?.id ?: hand.standingTiles.first().id
                    GameCommand.Discard(tileId)
                }
            }
        }
    }
}
