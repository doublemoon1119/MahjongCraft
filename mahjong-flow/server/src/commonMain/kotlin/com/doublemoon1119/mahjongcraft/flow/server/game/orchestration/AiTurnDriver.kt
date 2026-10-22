package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.DiscardReactionOptionsResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContext
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContextResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.isDrawWindowOpenFor
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import kotlin.uuid.Uuid

/**
 * 找出目前桌況下一個該行動的 AI 玩家與其命令，供 [GameFlowCoordinator] 驅動 AI 玩家自動出手。
 *
 * 只負責「找出該問誰、問完後決定的命令是什麼」，不負責把命令套用到桌況——那是呼叫端
 * （[GameFlowCoordinator]）的事，這裡不依賴 [GameActionRouter]/`GameFlowCoordinator` 本身，
 * 避免循環依賴。誰該行動、決策用的快照與合法動作，都來自同一次讀取的 [Game]，並隨結果一起回傳。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property getLegalActionsUseCase 依同一次讀取的桌況查詢玩家合法動作清單，直接重用，不重新實作規則判斷。
 * @property aiStrategyRegistry AI 策略登記中心，依 [Game.aiPlayerStrategyKeys] 中每位 AI 玩家的策略識別碼
 *           解析出實際要問的策略——每局、每個 AI 玩家可以各自使用不同策略，不是全伺服器共用一個。
 * @property visibilityPolicy 依 AI 玩家視角建立決策用快照的觀看政策。
 * @property moduleRegistry 規則模組註冊中心，用於提供規則特有但規則中立的決策限制。
 * @property decisionExecutor 在 AI 專用的調度器上呼叫策略，失敗或逾時時使用合法代操作。
 * @property commandResolver 依目前階段與規則限制產生安全的後備命令。
 * @property actionContextResolver 玩家目前操作情境的權威解析器。
 */
class AiTurnDriver(
    private val gameRepository: GameRepository,
    private val getLegalActionsUseCase: GetLegalActionsUseCase,
    private val aiStrategyRegistry: MahjongAiStrategyRegistry,
    private val visibilityPolicy: GameVisibilityPolicy,
    private val moduleRegistry: MahjongModuleRegistry,
    private val decisionExecutor: AiDecisionExecutor,
    private val commandResolver: AutomaticGameCommandResolver,
    private val actionContextResolver: PlayerActionContextResolver = PlayerActionContextResolver(),
) {
    /**
     * 找出目前桌況下下一個該行動的 AI 玩家與其命令；若沒有任何 AI 需要行動則回傳 null。
     *
     * 判斷順序（搶和反應 → 捨牌反應 → 自己回合）由 [PlayerActionContextResolver] 統一解析。摸牌
     * （[GameCommand.Draw]）不經過策略——這不是一個需要「策略」的決定，是每位玩家（人類/AI）
     * 回合開始時都必須做的機械動作，直接回傳固定命令。
     *
     * @param gameId 對局 Uuid。
     * @return 下一個該行動的 AI 玩家、其命令與決策依據的遊戲；沒有 AI 需要行動、對局已結束、對局不存在，或這一局已有
     *   策略呼叫正在計算時為 null。
     * @throws RuleContractViolationException 必須決策時沒有安全的後備命令。
     */
    suspend fun resolveNextAction(gameId: Uuid): AiTurnDecision? {
        val game = gameRepository.getGame(gameId) ?: return null
        if (game.isMatchOver) return null
        if (game.pendingRoundPreparation != null) return null
        val state = game.tableState

        val context = actionContextResolver.resolve(state).values.firstOrNull { context ->
            game.isAi(context.playerId)
        }
        if (context != null) {
            val phase = when (context) {
                is PlayerActionContext.RobbingReaction -> AiDecisionPhase.RespondingToRobbing
                is PlayerActionContext.DiscardReaction -> AiDecisionPhase.RespondingToDiscard
                is PlayerActionContext.OwnTurn -> AiDecisionPhase.OwnTurn
            }
            val command = decideGameCommand(game, context.playerId, phase) ?: return null
            return AiTurnDecision(context.playerId, command, basis = game)
        }

        val current = state.currentPlayer
        if (game.isAi(current.id) && state.isDrawWindowOpenFor(current.id)) {
            return AiTurnDecision(current.id, GameCommand.Draw, basis = game)
        }

        return null
    }

    /**
     * 依 [aiId] 在 [Game.aiPlayerStrategyKeys] 的策略 key 從 [aiStrategyRegistry] 解析出策略，組出 [AiDecisionContext]
     * 並經 [decisionExecutor] 問它該怎麼行動；策略失敗、逾時或舊工作尚未結束而無法再次詢問時，
     * 使用與真人逾時相同的安全代操作。
     *
     * @param game 決策所依據的權威遊戲。
     * @param aiId 要決策的 AI 玩家。
     * @param phase 玩家目前需要決策的階段。
     * @return 決定的命令；這一局已有策略呼叫正在計算時為 null。
     */
    private suspend fun decideGameCommand(
        game: Game,
        aiId: Uuid,
        phase: AiDecisionPhase,
    ): GameCommand? {
        val state = game.tableState
        val legalActionsResult = getLegalActionsUseCase.resolve(state, aiId)
        val legalActions = (legalActionsResult as? Outcome.Success)?.value ?: emptyList()
        val player = state.players.first { it.id == aiId }
        val strategyKey = game.aiPlayerStrategyKeys[aiId]
        val context = AiDecisionContext(
            snapshot = visibilityPolicy.snapshotFor(game, aiId),
            selfId = aiId,
            phase = phase,
            legalActions = legalActions,
            forcedDiscardTileId = moduleRegistry.getModule(state.config).forcedDiscardTileId(state, player),
            discardReactionId = state.pendingReaction?.id.takeIf { phase == AiDecisionPhase.RespondingToDiscard },
            discardReactionCommands = if (phase == AiDecisionPhase.RespondingToDiscard) {
                val pending = state.pendingReaction
                if (pending == null) {
                    emptyList()
                } else {
                    val discarder = state.players.firstOrNull { it.id == pending.discarderId }
                    val discardedEntry = discarder?.discardPile?.entries?.lastOrNull()
                    if (discarder == null || discardedEntry == null || discardedEntry.isTaken || discardedEntry.tile.id != pending.tileId) {
                        emptyList()
                    } else {
                        DiscardReactionOptionsResolver.resolveCandidates(
                            state = state,
                            module = moduleRegistry.getModule(state.config),
                            playerId = aiId,
                            discarderId = pending.discarderId,
                            discardedTile = discardedEntry.tile,
                        ).map { candidate ->
                            GameCommand.RespondToDiscard(
                                action = candidate.action,
                                reactionId = pending.id,
                                selectedTileIds = candidate.selectedTileIds,
                            )
                        }
                    }
                }
            } else {
                emptyList()
            },
        )
        val strategy = aiStrategyRegistry.resolve(strategyKey)
        return decisionExecutor.decide(
            gameId = game.id,
            playerId = aiId,
            strategyKey = strategyKey,
            decide = { strategy.decideGameCommand(context) },
            fallback = {
                commandResolver.resolve(state, aiId)
                    ?: throw RuleContractViolationException(
                        reasonId = GameAbortReasonIds.AUTOMATIC_DECISION_FAILED,
                        diagnostic = "AI $aiId in game ${game.id} has no safe fallback command for $phase",
                    )
            },
        )
    }
}
