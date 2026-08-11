package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/**
 * 找出目前桌況下一個該行動的 AI 玩家與其命令，供 [GameFlowCoordinator] 驅動 AI 玩家自動出手。
 *
 * 只負責「找出該問誰、問完後決定的命令是什麼」，不負責把命令套用到桌況——那是呼叫端
 * （[GameFlowCoordinator]）的事，這裡不依賴 [GameActionRouter]/`GameFlowCoordinator` 本身，
 * 避免循環依賴。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property getLegalActionsUseCase 查詢玩家目前合法動作清單的用例，直接重用，不重新實作規則判斷。
 * @property aiStrategyRegistry AI 策略登記中心，依每位 AI 玩家自己的 `aiStrategyKey` 解析出實際
 *           要問的策略——每局、每個 AI 玩家可以各自使用不同策略，不是全伺服器共用一個。
 */
@Factory
class AiTurnDriver(
    private val gameRepository: GameRepository,
    private val getLegalActionsUseCase: GetLegalActionsUseCase,
    private val aiStrategyRegistry: MahjongAiStrategyRegistry,
) {
    /**
     * 找出目前桌況下下一個該行動的 AI 玩家與其命令；若沒有任何 AI 需要行動則回傳 null。
     *
     * 判斷順序（搶槓反應 → 捨牌反應 → 自己回合）鏡射 [GetLegalActionsUseCase] 的既有慣例。摸牌
     * （[GameCommand.Draw]）不經過策略——這不是一個需要「策略」的決定，是每位玩家（人類/AI）
     * 回合開始時都必須做的機械動作，直接回傳固定命令。
     *
     * @param gameId 對局 Uuid。
     * @return 下一個該行動的 AI 玩家 Uuid 與其命令；沒有 AI 需要行動、或對局不存在時為 null。
     */
    suspend fun resolveNextAction(gameId: Uuid): Pair<Uuid, GameCommand>? {
        val state = gameRepository.getTableState(gameId) ?: return null

        val pendingChankan = state.pendingChankan
        if (pendingChankan != null) {
            val aiId = findEligibleAi(state, pendingChankan.eligiblePlayerIds, pendingChankan.responses.keys)
            if (aiId != null) return aiId to decide(gameId, state, aiId, AiDecisionPhase.RespondingToChankan)
        }

        val pendingReaction = state.pendingReaction
        if (pendingReaction != null) {
            val aiId = findEligibleAi(state, pendingReaction.eligiblePlayerIds, pendingReaction.responses.keys)
            if (aiId != null) return aiId to decide(gameId, state, aiId, AiDecisionPhase.RespondingToDiscard)
        }

        if (pendingChankan == null && pendingReaction == null) {
            val current = state.currentPlayer
            if (current.isAi) {
                // 剛碰/吃成立時，lastDrawn 也是 null，但這位玩家該做的是直接捨牌，不是摸牌——
                // 用 actionHistory 的最後一筆動作區分「回合剛開始，還沒摸牌」與「剛碰/吃，
                // 該直接捨牌」這兩種同樣 lastDrawn == null 的情境（明槓/暗槓/加槓皆會在套用副露
                // 的同一次呼叫裡立即補摸嶺上牌，套用後 lastDrawn 必定非 null，不會落入這裡）。
                val justClaimedMeld = current.actionHistory.lastOrNull().let { it is GameAction.Pon || it is GameAction.Chi }
                return if (current.hand.lastDrawn == null && !justClaimedMeld) {
                    current.id to GameCommand.Draw
                } else {
                    current.id to decide(gameId, state, current.id, AiDecisionPhase.OwnTurn)
                }
            }
        }

        return null
    }

    /**
     * 從 [eligibleIds] 裡找出第一位尚未回應（不在 [respondedIds] 裡）、且為 AI 的玩家。
     */
    private fun findEligibleAi(state: TableState, eligibleIds: Set<Uuid>, respondedIds: Set<Uuid>): Uuid? = eligibleIds.firstOrNull { id -> id !in respondedIds && state.players.first { it.id == id }.isAi }

    /**
     * 依 [aiId] 自己的 `aiStrategyKey` 從 [aiStrategyRegistry] 解析出策略，組出 [AiDecisionContext]
     * 並問它該怎麼行動。
     */
    private suspend fun decide(gameId: Uuid, state: TableState, aiId: Uuid, phase: AiDecisionPhase): GameCommand {
        val legalActionsResult = getLegalActionsUseCase(gameId, aiId)
        val legalActions = (legalActionsResult as? Outcome.Success)?.value ?: emptyList()
        val strategyKey = state.players.first { it.id == aiId }.aiStrategyKey
        val context = AiDecisionContext(
            snapshot = state.toSnapshot(setOf(aiId)),
            selfId = aiId,
            phase = phase,
            legalActions = legalActions,
        )
        return aiStrategyRegistry.resolve(strategyKey).decide(context)
    }
}
