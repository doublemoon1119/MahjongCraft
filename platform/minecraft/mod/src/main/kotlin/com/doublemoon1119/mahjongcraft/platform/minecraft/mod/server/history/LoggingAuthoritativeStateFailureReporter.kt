package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history

import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateFailureReporter
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import kotlin.uuid.Uuid

/** 把權威狀態交易中已隔離的牌譜記錄與提交通知失敗寫進伺服器日誌。 */
class LoggingAuthoritativeStateFailureReporter : AuthoritativeStateFailureReporter {
    /** 權威狀態交易失敗的專用 logger。 */
    private val logger = mahjongCraftLogger(LoggingAuthoritativeStateFailureReporter::class)

    override fun onHistoryRecordingFailed(matchId: Uuid, failure: Throwable) {
        logger.error("History events of match {} could not be recorded; its history has a gap from here", matchId, failure)
    }

    override fun onHistoryTableChangeUnavailable(matchId: Uuid, failure: Throwable) {
        logger.warn("A table change of match {} could not be recorded as a difference; the full table was saved instead", matchId, failure)
    }

    override fun onCommittedFactsListenerFailed(failure: Throwable) {
        logger.error("A committed game facts listener failed", failure)
    }
}
