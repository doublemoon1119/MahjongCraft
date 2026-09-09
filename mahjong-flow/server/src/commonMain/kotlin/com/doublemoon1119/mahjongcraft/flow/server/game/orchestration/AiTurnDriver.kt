package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContext
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContextResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
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
 * @property visibilityPolicy 依 AI 玩家視角建立決策用快照的觀看政策。
 * @property moduleRegistry 規則模組註冊中心，用於提供規則特有但規則中立的決策限制。
 * @property actionContextResolver 玩家目前操作情境的權威解析器。
 */
@Factory
class AiTurnDriver(
    private val gameRepository: GameRepository,
    private val getLegalActionsUseCase: GetLegalActionsUseCase,
    private val aiStrategyRegistry: MahjongAiStrategyRegistry,
    private val visibilityPolicy: GameVisibilityPolicy,
    private val moduleRegistry: MahjongModuleRegistry,
    private val actionContextResolver: PlayerActionContextResolver = PlayerActionContextResolver(),
) {
    /**
     * 找出目前桌況下下一個該行動的 AI 玩家與其命令；若沒有任何 AI 需要行動則回傳 null。
     *
     * 判斷順序（搶槓反應 → 捨牌反應 → 自己回合）由 [PlayerActionContextResolver] 統一解析。摸牌
     * （[GameCommand.Draw]）不經過策略——這不是一個需要「策略」的決定，是每位玩家（人類/AI）
     * 回合開始時都必須做的機械動作，直接回傳固定命令。
     *
     * @param gameId 對局 Uuid。
     * @return 下一個該行動的 AI 玩家 Uuid 與其命令；沒有 AI 需要行動、對局已結束、或對局不存在時為
     *   null。
     */
    suspend fun resolveNextAction(gameId: Uuid): Pair<Uuid, GameCommand>? {
        val game = gameRepository.getGame(gameId) ?: return null
        if (game.isMatchOver) return null
        val state = game.tableState

        val context = actionContextResolver.resolve(state).values.firstOrNull { context ->
            state.players.first { it.id == context.playerId }.isAi
        }
        if (context != null) {
            val phase = when (context) {
                is PlayerActionContext.KanReaction -> AiDecisionPhase.RespondingToKan
                is PlayerActionContext.DiscardReaction -> AiDecisionPhase.RespondingToDiscard
                is PlayerActionContext.OwnTurn -> AiDecisionPhase.OwnTurn
            }
            return context.playerId to decideGameCommand(gameId, game, context.playerId, phase)
        }

        val current = state.currentPlayer
        if (current.isAi &&
            current.hand.lastDrawn == null &&
            !current.justClaimedMeld &&
            state.pendingKanReaction == null &&
            state.pendingReaction == null
        ) {
            return current.id to GameCommand.Draw
        }

        return null
    }

    /**
     * 依 [aiId] 自己的 `aiStrategyKey` 從 [aiStrategyRegistry] 解析出策略，組出 [AiDecisionContext]
     * 並問它該怎麼行動。
     */
    private suspend fun decideGameCommand(
        gameId: Uuid,
        game: Game,
        aiId: Uuid,
        phase: AiDecisionPhase,
    ): GameCommand {
        val state = game.tableState
        val legalActionsResult = getLegalActionsUseCase(gameId, aiId)
        val legalActions = (legalActionsResult as? Outcome.Success)?.value ?: emptyList()
        val player = state.players.first { it.id == aiId }
        val strategyKey = player.aiStrategyKey
        val context = AiDecisionContext(
            snapshot = visibilityPolicy.snapshotFor(game, aiId),
            selfId = aiId,
            phase = phase,
            legalActions = legalActions,
            forcedDiscardTileId = moduleRegistry.getModule(state.config).forcedDiscardTileId(state, player),
        )
        return aiStrategyRegistry.resolve(strategyKey).decideGameCommand(context)
    }
}
