package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.ai.RoundPreparationAiContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.accepts
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import kotlinx.coroutines.CancellationException
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/**
 * 需要由伺服器代為完成的一次開局準備提交。
 *
 * @property playerId 提交的參與者。
 * @property command 要送出的提交命令。
 * @property clearsForcedAutoPlay 參與者是否為逾時的真人；提交前須先解除他的強制自動操作。
 * @property basis 提交內容所依據的權威遊戲；AI 的提交只應在權威遊戲仍是這個遊戲時套用。
 */
data class AutomatedRoundPreparation(
    val playerId: Uuid,
    val command: GameCommand.SubmitRoundPreparation,
    val clearsForcedAutoPlay: Boolean,
    val basis: Game,
)

/**
 * 解析 AI 與逾時真人的下一次開局準備提交。
 *
 * AI 的提交經 [decisionExecutor] 在 AI 專用的調度器上詢問策略；沒有在等待上限內得到結果、策略丟出例外，或結果不被接受時，
 * 使用解析器提供的可重現提交。
 *
 * @property gameRepository 讀取權威遊戲。
 * @property moduleRegistry 依對局設定取得規則模組。
 * @property resolverRegistry 各規則的開局準備解析器。
 * @property aiStrategyRegistry 依策略 key 取得 AI 策略。
 * @property visibilityPolicy 建立 AI 視角的快照。
 * @property decisionExecutor 在 AI 專用的調度器上呼叫策略。
 */
@Factory
class RoundPreparationAiDriver(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val resolverRegistry: RoundPreparationResolverRegistry,
    private val aiStrategyRegistry: MahjongAiStrategyRegistry,
    private val visibilityPolicy: GameVisibilityPolicy,
    private val decisionExecutor: AiDecisionExecutor,
) {
    /** 找出下一位需要自動提交的參與者；沒有時，或這一局已有策略呼叫正在計算時回傳 null。 */
    suspend fun resolveNextAction(gameId: Uuid): AutomatedRoundPreparation? {
        val game = gameRepository.getGame(gameId) ?: return null
        val preparation = game.pendingRoundPreparation ?: return null
        val module = moduleRegistry.getModule(game.tableState.config)
        val resolver = resolverRegistry.find(module.id) ?: return null
        val pendingIds = preparation.participantPlayerIds - preparation.completedPlayerIds
        val player = game.tableState.players.firstOrNull { player ->
            player.id in pendingIds && (game.isAi(player.id) || player.id in game.forcedAutoPlayPlayerIds)
        } ?: return null
        val input = preparation.inputSpecsByPlayerId.getValue(player.id)
        val fallback = resolver.fallbackSubmission(game.tableState, preparation, player.id, module)
        val submission = if (game.isAi(player.id)) {
            val strategyKey = game.aiPlayerStrategyKeys[player.id]
            val strategy = aiStrategyRegistry.resolve(strategyKey)
            val context = RoundPreparationAiContext(
                snapshot = visibilityPolicy.snapshotFor(game, player.id),
                selfId = player.id,
                stepId = preparation.stepId,
                inputSpec = input,
            )
            decisionExecutor.decide(
                gameId = gameId,
                playerId = player.id,
                strategyKey = strategyKey,
                decide = {
                    runCatching { strategy.decideRoundPreparation(context) }.getOrElse { error ->
                        if (error is CancellationException) throw error
                        fallback
                    }
                },
                fallback = { fallback },
            ) ?: return null
        } else {
            fallback
        }
        val validated = submission.takeIf {
            input.accepts(it) && resolver.accepts(game.tableState, preparation, player.id, it, module)
        } ?: fallback
        return AutomatedRoundPreparation(
            playerId = player.id,
            command = GameCommand.SubmitRoundPreparation(validated),
            clearsForcedAutoPlay = player.id in game.forcedAutoPlayPlayerIds,
            basis = game,
        )
    }
}
