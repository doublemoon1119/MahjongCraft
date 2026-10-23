package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.ai.RuleDecisionAiContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.accepts
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import kotlin.uuid.Uuid

/**
 * 解析 AI 與逾時真人的下一次規則選擇提交。
 *
 * AI 的提交經 [decisionExecutor] 在 AI 專用的調度器上詢問策略；沒有在等待上限內得到結果、策略丟出例外，或結果不被接受時，
 * 使用解析器提供的可重現提交。
 *
 * @property gameRepository 讀取權威遊戲。
 * @property moduleRegistry 依對局設定取得規則模組。
 * @property resolverRegistry 各規則的規則選擇解析器。
 * @property aiStrategyRegistry 依策略 key 取得 AI 策略。
 * @property visibilityPolicy 建立 AI 視角的快照。
 * @property decisionExecutor 在 AI 專用的調度器上呼叫策略。
 */
class RuleDecisionAiDriver(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val resolverRegistry: RuleDecisionResolverRegistry,
    private val aiStrategyRegistry: MahjongAiStrategyRegistry,
    private val visibilityPolicy: GameVisibilityPolicy,
    private val decisionExecutor: AiDecisionExecutor,
) {
    /**
     * 找出下一位需要自動提交的參與者；沒有時，或這一局已有策略呼叫正在計算時回傳 null。
     *
     * @param gameId 欲解析規則選擇提交的對局。
     * @return 可提交的玩家與命令；目前無自動提交時為 null。
     * @throws RuleContractViolationException 策略與預設提交都不符合目前選擇步驟。
     */
    suspend fun resolveNextAction(gameId: Uuid): AutomatedRuleDecision? {
        val game = gameRepository.getGame(gameId) ?: return null
        val ruleDecision = game.pendingRuleDecision ?: return null
        val module = moduleRegistry.getModule(game.tableState.config)
        val resolver = resolverRegistry.find(module.id) ?: return null
        val pendingIds = ruleDecision.participantPlayerIds - ruleDecision.completedPlayerIds
        val player = game.tableState.players.firstOrNull { player ->
            player.id in pendingIds && (game.isAi(player.id) || player.id in game.forcedAutoPlayPlayerIds)
        } ?: return null
        val input = ruleDecision.inputSpecsByPlayerId.getValue(player.id)
        val fallback = resolver.fallbackSubmission(game.tableState, ruleDecision, player.id, module)
        val submission = if (game.isAi(player.id)) {
            val strategyKey = game.aiPlayerStrategyKeys[player.id]
            val strategy = aiStrategyRegistry.resolve(strategyKey)
            val context = RuleDecisionAiContext(
                snapshot = visibilityPolicy.snapshotFor(game, player.id),
                selfId = player.id,
                timing = ruleDecision.timing,
                stepId = ruleDecision.stepId,
                inputSpec = input,
            )
            decisionExecutor.decide(
                gameId = gameId,
                playerId = player.id,
                strategyKey = strategyKey,
                decide = { strategy.decideRuleDecision(context) },
                fallback = { fallback },
            ) ?: return null
        } else {
            fallback
        }
        val validated = submission.takeIf {
            input.accepts(it) && resolver.accepts(game.tableState, ruleDecision, player.id, it, module)
        } ?: fallback
        if (!input.accepts(validated) || !resolver.accepts(game.tableState, ruleDecision, player.id, validated, module)) {
            throw RuleContractViolationException(
                GameAbortReasonIds.AUTOMATIC_DECISION_FAILED,
                "Rule decision ${ruleDecision.stepId} has no valid automatic submission for ${player.id}",
            )
        }
        return AutomatedRuleDecision(
            playerId = player.id,
            command = GameCommand.SubmitRuleDecision(ruleDecision.id, validated),
            clearsForcedAutoPlay = player.id in game.forcedAutoPlayPlayerIds,
            basis = game,
        )
    }
}
