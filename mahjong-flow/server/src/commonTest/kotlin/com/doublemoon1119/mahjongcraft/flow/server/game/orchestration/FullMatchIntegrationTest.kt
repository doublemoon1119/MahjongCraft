package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceRoundUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareExhaustiveDrawUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareKanUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareKyuushuKyuuhaiUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareRiichiUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareSuukanNagareUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareTsumoUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DiscardTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DrawTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToChankanUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToDiscardUseCase
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
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
 * 不會卡住、分數守恆——這是目前完全空缺的一層測試：既有測試都只針對單一 use case 的窄範圍驗證，
 * 從沒有一個測試證明整條編排鏈路真的能把一整場牌局打完。`driveAiPlayers` 先前的無限迴圈 bug
 * 正是在接近這種整合情境時才被發現的。
 *
 * 刻意用 [FakeAiStrategy]（見下方）而非 [com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy]：
 * 這個測試關心的是「編排層撐不撐得住」，不是「AI 選得好不好」，用固定策略讓行為可預期、測試結果
 * 穩定重現，不需要處理隨機性帶來的不穩定。
 */
class FullMatchIntegrationTest {

    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val eventPublisher = FakeGameEventPublisher()
        val router = GameActionRouter(
            drawTileUseCase = DrawTileUseCase(gameRepo, moduleRegistry, snapshotRepo, eventPublisher),
            discardTileUseCase = DiscardTileUseCase(gameRepo, moduleRegistry, snapshotRepo, eventPublisher),
            declareRiichiUseCase = DeclareRiichiUseCase(gameRepo, moduleRegistry, snapshotRepo, eventPublisher),
            declareTsumoUseCase = DeclareTsumoUseCase(gameRepo, moduleRegistry, snapshotRepo, eventPublisher),
            declareKanUseCase = DeclareKanUseCase(gameRepo, moduleRegistry, snapshotRepo, eventPublisher),
            respondToDiscardUseCase = RespondToDiscardUseCase(gameRepo, moduleRegistry, snapshotRepo, eventPublisher),
            respondToChankanUseCase = RespondToChankanUseCase(gameRepo, moduleRegistry, snapshotRepo, eventPublisher),
            declareKyuushuKyuuhaiUseCase = DeclareKyuushuKyuuhaiUseCase(gameRepo, moduleRegistry, snapshotRepo, eventPublisher),
        )
        val getLegalActionsUseCase = GetLegalActionsUseCase(gameRepo, moduleRegistry)
        val aiStrategyRegistry = MahjongAiStrategyRegistryImpl(defaultKey = FakeAiStrategy.KEY).apply {
            register(FakeAiStrategy.KEY) { FakeAiStrategy() }
        }
        val aiTurnDriver = AiTurnDriver(gameRepo, getLegalActionsUseCase, aiStrategyRegistry)
        val coordinator = GameFlowCoordinator(
            gameActionRouter = router,
            gameRepository = gameRepo,
            moduleRegistry = moduleRegistry,
            declareExhaustiveDrawUseCase = DeclareExhaustiveDrawUseCase(gameRepo, moduleRegistry, snapshotRepo, eventPublisher),
            declareSuukanNagareUseCase = DeclareSuukanNagareUseCase(gameRepo, moduleRegistry, snapshotRepo, eventPublisher),
            advanceRoundUseCase = AdvanceRoundUseCase(gameRepo, moduleRegistry, snapshotRepo, eventPublisher),
            aiTurnDriver = aiTurnDriver,
        )
    }

    /**
     * 驗證 4 位 AI 玩家能把整場東風戰（4 局，含可能的連莊）打完，不會卡住、分數守恆。
     *
     * 判斷「整場對局真的打完了」的方式：重複呼叫 [GameFlowCoordinator.driveAiPlayers]
     * 足夠多次後，再多呼叫一次不應該再讓桌況產生任何變化（沒有變化代表沒有任何 AI 還需要行動、
     * 也沒有任何系統銜接還能推進——這與 `driveAiPlayers` 內部判斷「沒有進展」的機制完全一致）；
     * 若桌況在這麼多次呼叫後仍持續變化，代表迴圈次數不夠或編排邏輯卡在某處，測試會直接失敗。
     */
    @Test
    fun `test four ai players play a full east-only match to completion`() = runTest {
        val fixtures = Fixtures()
        val gameId = Uuid.random()
        val playerIds = List(4) { Uuid.random() }
        val config = RiichiRuleConfig(gameLength = RiichiGameLength.East)
        val module = fixtures.moduleRegistry.getModule(config)
        val initialState = GameInitializer.initialize(
            id = gameId,
            playerIds = playerIds,
            module = module,
            aiPlayerStrategyKeys = playerIds.associateWith { FakeAiStrategy.KEY },
        )
        fixtures.gameRepo.setTableState(initialState)

        // 東風戰 4 局（含可能的連莊）粗估最多需要幾千步 AI 動作；driveAiPlayers 單次呼叫有
        // repeat(100) 上限，這裡從外層重複呼叫足夠多次，讓內部的迭代預算加總起來遠超過實際需求。
        repeat(40) {
            fixtures.coordinator.driveAiPlayers(gameId)
        }

        val stateBeforeFinalCall = fixtures.gameRepo.getTableState(gameId)
        fixtures.coordinator.driveAiPlayers(gameId)
        val finalState = fixtures.gameRepo.getTableState(gameId)

        assertEquals(
            stateBeforeFinalCall,
            finalState,
            "The table should have completely stabilized by now; if it's still changing, either the match " +
                "genuinely didn't finish within the iteration budget, or the orchestration is stuck.",
        )
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

        override suspend fun decide(context: AiDecisionContext): GameCommand = when (context.phase) {
            AiDecisionPhase.RespondingToDiscard ->
                GameCommand.RespondToDiscard(context.legalActions.firstOrNull { it is GameAction.Ron } ?: GameAction.Pass)

            AiDecisionPhase.RespondingToChankan ->
                GameCommand.RespondToChankan(context.legalActions.firstOrNull { it is GameAction.Ron } ?: GameAction.Pass)

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
