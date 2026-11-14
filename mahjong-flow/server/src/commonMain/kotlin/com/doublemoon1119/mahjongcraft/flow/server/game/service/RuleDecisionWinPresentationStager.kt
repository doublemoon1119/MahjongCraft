package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundOutcomePresentationClassification
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SettledWinPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationWinner
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionWin
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionContext
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 將規則選擇步驟直接成立的和牌交給共用胡牌演出與結算呈現。
 *
 * 只準備呈現資料；權威的和牌結果已由規則選擇流程寫入對局狀態。
 *
 * @property handoff 同一次命令內交給結束本局流程的胡牌呈現。
 * @property detailResolverRegistry 規則提供的結算詳情解析器。
 */
class RuleDecisionWinPresentationStager(
    private val handoff: WinPresentationHandoff,
    private val detailResolverRegistry: WinSettlementDetailResolverRegistry,
) {
    /**
     * 建立共用結算與胡牌演出，供同一次命令的本局結束流程取用。
     *
     * @param gameId 對局識別碼。
     * @param previousState 結算前的權威桌況。
     * @param currentState 結算後的權威桌況。
     * @param module 本局使用的規則模組。
     * @param aiPlayerIds 由 AI 操控的玩家。
     * @param win 規則選擇步驟直接成立的和牌。
     */
    fun stage(
        gameId: Uuid,
        previousState: TableState,
        currentState: TableState,
        module: MahjongRuleModule<*>,
        aiPlayerIds: Set<Uuid>,
        win: RuleDecisionWin,
    ) {
        val outcome = win.outcome
        val transitionDirective = module.resolveRoundTransition(
            context = RoundTransitionContext(
                tableState = currentState,
                outcomeId = outcome.outcomeId,
                classification = RoundCompletionClassification.WIN,
                beneficiaryPlayerIds = outcome.winnerPlayerIds,
                responsiblePlayerIds = outcome.responsiblePlayerIds,
            ),
        )
        val resolvedOutcome = ResolvedRoundOutcome(
            id = outcome.outcomeId,
            settledTableState = currentState,
            beneficiaryPlayerIds = outcome.winnerPlayerIds,
            responsiblePlayerIds = outcome.responsiblePlayerIds,
            scoreDeltas = win.scoreDeltas,
            transitionDirective = transitionDirective,
            presentationClassification = RoundOutcomePresentationClassification.WIN_EQUIVALENT,
            extensionDetail = win.extensionDetail,
        )
        handoff.stage(
            gameId = gameId,
            presentation = SettledWinPresentation(
                winnerPlayerIds = outcome.winnerPlayerIds,
                celebration = WinCelebrationRequest(
                    winningTileId = outcome.winningTileId,
                    isTsumo = outcome.responsiblePlayerIds.isEmpty(),
                    revealsWinnerHands = outcome.revealsWinnerHands,
                    winners = outcome.winnerPlayerIds.map { winnerId ->
                        WinCelebrationWinner(currentState.players.indexOfFirst { it.id == winnerId })
                    },
                ),
                settlement = WinSettlementPresentationRequestFactory.createSpecialOutcome(
                    previousState = previousState,
                    outcome = resolvedOutcome,
                    module = module,
                    aiPlayerIds = aiPlayerIds,
                    detailResolverRegistry = detailResolverRegistry,
                    revealsWinnerHands = outcome.revealsWinnerHands,
                ),
            ),
        )
    }
}
