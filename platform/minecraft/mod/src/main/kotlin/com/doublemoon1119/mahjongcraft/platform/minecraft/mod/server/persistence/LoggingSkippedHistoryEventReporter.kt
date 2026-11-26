package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence

import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.SkippedHistoryEventReporter
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import kotlin.uuid.Uuid

/** 把保存或讀取權威存檔時略過的歷史事件寫進日誌。 */
class LoggingSkippedHistoryEventReporter : SkippedHistoryEventReporter {
    /** 略過歷史事件的專用 logger。 */
    private val logger = mahjongCraftLogger(LoggingSkippedHistoryEventReporter::class)

    override fun onSkipped(matchId: Uuid, sequence: Long, failure: Throwable) {
        logger.error(
            "History event {} of match {} could not be converted and was skipped; its history has a gap from here",
            sequence,
            matchId,
            failure,
        )
    }
}
