package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionReporter
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 把 AI 決策沒有等到策略結果的情況記成 warn。 */
@Single(binds = [AiDecisionReporter::class])
class FabricAiDecisionReporter : AiDecisionReporter {
    /** AI 決策逾時的專用 logger。 */
    private val logger = mahjongCraftLogger(FabricAiDecisionReporter::class)

    override fun onTimedOut(gameId: Uuid, playerId: Uuid, strategyKey: String?) {
        logger.warn("AI decision for player {} in game {} with strategy {} timed out; using the fixed command", playerId, gameId, strategyKey)
    }

    override fun onPreviousStillRunning(gameId: Uuid, playerId: Uuid, strategyKey: String?) {
        logger.warn(
            "A timed out AI decision in game {} is still running; using the fixed command for player {} with strategy {} until it ends",
            gameId,
            playerId,
            strategyKey,
        )
    }
}
