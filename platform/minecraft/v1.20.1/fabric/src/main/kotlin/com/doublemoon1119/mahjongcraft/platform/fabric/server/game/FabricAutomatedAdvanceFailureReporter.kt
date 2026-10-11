package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomatedAdvanceFailureReporter
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 把自動推進的失敗記成 error。 */
@Single(binds = [AutomatedAdvanceFailureReporter::class])
class FabricAutomatedAdvanceFailureReporter : AutomatedAdvanceFailureReporter {
    /** 自動推進失敗的專用 logger。 */
    private val logger = mahjongCraftLogger(FabricAutomatedAdvanceFailureReporter::class)

    override fun onAdvanceFailed(gameId: Uuid, error: Exception) {
        logger.error("Failed to advance automated players for game {}", gameId, error)
    }
}
