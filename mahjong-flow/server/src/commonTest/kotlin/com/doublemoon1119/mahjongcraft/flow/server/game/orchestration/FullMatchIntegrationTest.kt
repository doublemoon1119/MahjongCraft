package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
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
 * 完全透過 [GameFlowCoordinator] 驅動，不經過任何 Minecraft 平台層。
 *
 * 目的是驗證編排層本身（[GameFlowCoordinator]/[AiTurnDriver]/連莊過莊自動銜接）撐得住一整場對局，
 * 不會卡住、分數守恆。對局環境使用與 AI 對局模擬共用的 [SimulationRuntime]。
 *
 * 刻意用 [FakeAiStrategy]（見下方）而非 [RandomAiStrategy]：
 * 這個測試關心的是「編排層撐不撐得住」，不是「AI 選得好不好」，用固定策略讓行為可預期、測試結果
 * 穩定重現，不需要處理隨機性帶來的不穩定。
 */
class FullMatchIntegrationTest {
    /**
     * 驗證 4 位 AI 玩家能把整場東風戰（4 局，含可能的連莊）打完，不會卡住、分數守恆。
     *
     * 只呼叫一次 [GameFlowCoordinator.driveAutomatedPlayers]——它內部會自動開好幾批次直到真的收斂
     * 為止（見其 KDoc），不需要呼叫端自己重複呼叫湊迭代預算；若真的卡在某處跑不完，這裡會直接拋出
     * `IllegalStateException`，測試會直接失敗並帶出清楚的錯誤訊息，不需要額外再比較前後桌況。
     */
    @Test
    fun `test four ai players play a full east-only match to completion`() = runTest {
        val runtime = SimulationRuntime(defaultStrategyKey = FakeAiStrategy.KEY).apply {
            aiStrategyRegistry.register(FakeAiStrategy.KEY) { FakeAiStrategy() }
        }
        val gameId = Uuid.random()
        val playerIds = List(4) { Uuid.random() }
        val config = RiichiRuleConfig(gameLength = RiichiGameLength.East)
        val module = runtime.moduleRegistry.getModule(config)
        val initialState = GameInitializer.initialize(
            id = gameId,
            playerIds = playerIds,
            module = module,
            aiPlayerStrategyKeys = playerIds.associateWith { FakeAiStrategy.KEY },
        ).tableState
        runtime.gameRepository.setTableState(initialState)

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
    }

    /**
     * 供整場對局整合測試使用的固定策略：刻意不主動鳴牌（吃/碰/槓/立直/九種九牌），只在自然出現
     * 榮和/自摸機會時才拿，其餘時候單純摸牌後打出剛摸到的牌——這個測試關心的是編排層撐不撐得住
     * 一整場對局，不是驗證每種行牌路徑，維持策略單純、行為可預期比覆蓋率更重要（各種鳴牌/立直/
     * 搶槓路徑已經有各自獨立的單元測試涵蓋）。
     */
    private class FakeAiStrategy : MahjongAiStrategy {
        companion object {
            const val KEY = "fake-deterministic"
        }

        override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand = when (context.phase) {
            AiDecisionPhase.RespondingToDiscard ->
                GameCommand.RespondToDiscard(
                    context.legalActions.firstOrNull { it is GameAction.Ron }
                        ?: GameAction.Pass,
                )

            AiDecisionPhase.RespondingToKan ->
                GameCommand.RespondToKan(
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
