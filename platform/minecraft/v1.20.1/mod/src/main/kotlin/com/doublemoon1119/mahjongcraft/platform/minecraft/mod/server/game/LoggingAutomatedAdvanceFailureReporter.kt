package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomatedAdvanceFailureReporter
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import kotlin.uuid.Uuid

/** 把自動推進的失敗記成 error。 */
class LoggingAutomatedAdvanceFailureReporter : AutomatedAdvanceFailureReporter {
    /** 自動推進失敗的專用 logger。 */
    private val logger = mahjongCraftLogger(LoggingAutomatedAdvanceFailureReporter::class)

    override fun onAdvanceFailed(gameId: Uuid, error: Exception) {
        logger.error("Failed to advance automated players for game {}", gameId, error)
    }
}
