package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.history.acceptedActionHistoryDraft
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.revealHandsOf
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.ExhaustiveDrawSettlementResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.SettlementExtrasOccasion
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionContext
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 宣告一般流局（牌山摸盡）的實例化用例。
 *
 * 這是「流局判定」系列子項的第一塊：處理牌山摸盡觸發的一般流局結算（如聽牌／不聽罰符），
 * 是後續子項（九種九牌、三家和了流局、四風連打、四家立直）共用的機制骨架。
 *
 * 系統觸發、無 `playerId` 參數——一般流局不是玩家主動發起的操作，理由與
 * [AdvanceRoundUseCase] 相同。由誰、在什麼時機呼叫本用例（例如伺服器捕捉到 [DrawTileUseCase]
 * 回傳 [GameError.WallExhausted] 後接著呼叫）是更外層（伺服器流程編排）的決定，不在這裡處理；
 * 呼叫前應確認牌山確實已經摸盡。
 *
 * 聽牌判定與不聽罰符拆分等規則相關的計算完全交給
 * [MahjongRuleModule.declareExhaustiveDraw]
 * 處理——這裡刻意不轉型成任何規則專屬的具體型別，理由與 [DeclareTsumoUseCase] 相同。
 *
 * 只把 [GameAction.ExhaustiveDraw] 記錄進聽牌玩家的 `actionHistory`，不聽的玩家不記錄，
 * 供後續事件與呈現流程保留這次流局的玩家狀態。連莊與否則會明確寫入局結束摘要的
 * [RoundTransitionDirective]，不從動作紀錄反向推定。
 *
 * 對局排定了 [PendingGameTransition.SettleExhaustiveDraw] 時，結算與清除這項排定在同一筆寫入完成。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的規則模組。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property eventPublisher 對局通知服務。
 */
class DeclareExhaustiveDrawUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val eventPublisher: GameEventPublisher,
) {
    /**
     * 執行一般流局宣告邏輯。
     *
     * @param gameId 對局 Uuid。
     * @return 執行結果，成功時帶出第一次結算的權威桌況與規則結果，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid): Outcome<DeclaredExhaustiveDraw, GameError> {
        // 1. 以原子方式讀取桌況、計算流局結算並寫回
        val outcome = gameRepository.updateGame(
            gameId,
            history = { before, after, result ->
                if (result is Outcome.Success && before != null && after != null) {
                    listOf(
                        acceptedActionHistoryDraft(
                            null,
                            GameAction.ExhaustiveDraw(result.value.settlement.reason),
                            before.tableState,
                            after.tableState,
                        ),
                    )
                } else {
                    emptyList<HistoryEventDraft>()
                }
            },
        ) { game ->
            val state = game?.tableState
            when {
                game == null || state == null -> game to Outcome.Error(GameError.GameNotFound(gameId))
                else -> {
                    val module = moduleRegistry.getModule(state.config)

                    // 規則已提供一般流局能力卻無法產生結算，代表規則契約失效，不能把它當成零分流局繼續。
                    val settlement = module.declareExhaustiveDraw(state)
                        ?: throw RuleContractViolationException(
                            reasonId = GameAbortReasonIds.MISSING_DRAW_SETTLEMENT,
                            diagnostic = "Rule module ${module.id} did not provide an exhaustive draw settlement",
                        )

                    // 這次流局有玩家該收下場上供託時，候選者從莊家起算排列，歸屬交給規則決定。
                    val extras = settlement.stickPotCollectorPlayerIds.takeIf { it.isNotEmpty() }?.let { collectorIds ->
                        SettlementExtrasResolver.resolve(
                            state = state,
                            module = module,
                            occasion = SettlementExtrasOccasion.EXHAUSTIVE_DRAW,
                            candidatePlayerIds = state.playersInTurnOrderFrom(state.dealerPlayerId, collectorIds),
                        )
                    }
                    val extraDeltas = extras?.scoreDeltas.orEmpty()

                    val finalDeltas = (settlement.scoreDeltas.keys + extraDeltas.keys).associateWith { id ->
                        (settlement.scoreDeltas[id] ?: 0) + (extraDeltas[id] ?: 0)
                    }

                    val updatedPlayers = state.players.map { player ->
                        val withScore = player.copy(score = player.score + (finalDeltas[player.id] ?: 0))
                        if (player.id in settlement.tenpaiPlayerIds) {
                            withScore.recordAction(GameAction.ExhaustiveDraw(settlement.reason))
                        } else {
                            withScore
                        }
                    }
                    val newState = state.copy(
                        players = updatedPlayers,
                        dynamicRuleState = extras?.dynamicRuleState ?: state.dynamicRuleState,
                        revealedHandSettlements = settlement.revealedHands,
                    ).revealHandsOf(settlement.revealedHands.map { it.playerId })

                    val transitionDirective = module.resolveRoundTransition(
                        RoundTransitionContext(
                            tableState = newState,
                            outcomeId = settlement.reason.id,
                            classification = RoundCompletionClassification.EXHAUSTIVE_DRAW,
                            beneficiaryPlayerIds = settlement.tenpaiPlayerIds,
                            responsiblePlayerIds = emptySet(),
                            exhaustiveDrawReason = settlement.reason,
                        ),
                    )
                    game.copy(
                        tableState = newState,
                        // 排定的結算在同一筆寫入中消耗，重複接續也不會再結算一次。
                        pendingTransition = game.pendingTransition.takeUnless { it == PendingGameTransition.SettleExhaustiveDraw },
                        roundCompletion = RoundCompletionSummary(
                            outcomeId = settlement.reason.id,
                            classification = RoundCompletionClassification.EXHAUSTIVE_DRAW,
                            beneficiaryPlayerIds = settlement.tenpaiPlayerIds,
                            transitionDirective = transitionDirective,
                            settledScoresByPlayerId = newState.players.associate { it.id to it.score },
                        ),
                    ) to Outcome.Success(ExhaustiveDrawResult(newState, settlement))
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        val newState = result.tableState

        // 2. 同步快照給所有正在觀察的玩家
        snapshotSynchronizer.syncAll(gameId)

        // 3. 廣播流局事件；跟 GameAction.RoundStarted 一樣沒有實際執行者，比照既有慣例填入莊家 Uuid
        val dealerId = newState.dealerPlayerId
        eventPublisher.publishToAllObservers(
            gameId,
            newState.players.map { it.id },
            dealerId,
            GameAction.ExhaustiveDraw(result.settlement.reason),
        )

        return Outcome.Success(
            DeclaredExhaustiveDraw(
                tableState = result.tableState,
                settlement = result.settlement,
            ),
        )
    }

    /**
     * `update` 區塊內部使用的中繼結果，讓第一次計算出的 [settlement] 能跟著 [tableState] 一起帶出
     * `gameRepository.update` 的作用域，供後續同步與呈現使用；不能在外層重新呼叫規則模組計算。
     *
     * @property tableState 流局結算後的權威桌況。
     * @property settlement 實際成立的流局結算與公開手牌資訊。
     */
    private data class ExhaustiveDrawResult(
        val tableState: TableState,
        val settlement: ExhaustiveDrawSettlementResult,
    )
}
