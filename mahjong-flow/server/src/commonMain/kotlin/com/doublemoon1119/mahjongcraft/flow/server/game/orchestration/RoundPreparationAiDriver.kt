package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.ai.RoundPreparationAiContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.accepts
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/** 需要由伺服器代為完成的一次開局準備提交。 */
data class AutomatedRoundPreparation(
    val playerId: Uuid,
    val command: GameCommand.SubmitRoundPreparation,
    val clearsForcedAutoPlay: Boolean,
)

/** 解析 AI 與逾時真人的下一次開局準備提交。 */
@Factory
class RoundPreparationAiDriver(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val resolverRegistry: RoundPreparationResolverRegistry,
    private val aiStrategyRegistry: MahjongAiStrategyRegistry,
    private val visibilityPolicy: GameVisibilityPolicy,
) {
    /** 找出下一位需要自動提交的參與者；沒有時回傳 null。 */
    suspend fun resolveNextAction(gameId: Uuid): AutomatedRoundPreparation? {
        val game = gameRepository.getGame(gameId) ?: return null
        val preparation = game.pendingRoundPreparation ?: return null
        val module = moduleRegistry.getModule(game.tableState.config)
        val resolver = resolverRegistry.find(module.id) ?: return null
        val pendingIds = preparation.participantPlayerIds - preparation.completedPlayerIds
        val player = game.tableState.players.firstOrNull { player ->
            player.id in pendingIds && (player.isAi || player.id in game.forcedAutoPlayPlayerIds)
        } ?: return null
        val input = preparation.inputSpecsByPlayerId.getValue(player.id)
        val fallback = resolver.fallbackSubmission(game.tableState, preparation, player.id, module)
        val submission = if (player.isAi) {
            runCatching {
                withTimeoutOrNull(AI_DECISION_TIMEOUT_MILLIS) {
                    aiStrategyRegistry.resolve(player.aiStrategyKey).decideRoundPreparation(
                        RoundPreparationAiContext(
                            snapshot = visibilityPolicy.snapshotFor(game, player.id),
                            selfId = player.id,
                            stepId = preparation.stepId,
                            inputSpec = input,
                        ),
                    )
                } ?: fallback
            }.getOrElse { fallback }
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
        )
    }

    private companion object {
        /** Preparation AI 單次決策的硬上限；逾時後改用 resolver 的 deterministic fallback。 */
        const val AI_DECISION_TIMEOUT_MILLIS: Long = 5_000L
    }
}
