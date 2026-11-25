package com.doublemoon1119.mahjongcraft.testing.flow.bundled

import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.SkippedHistoryEventReporter

/** 不應略過任何歷史事件的測試使用。收到略過時直接讓測試失敗。 */
val failOnSkippedHistoryEvent: SkippedHistoryEventReporter = SkippedHistoryEventReporter { matchId, sequence, failure ->
    throw AssertionError("History event $sequence of match $matchId was skipped unexpectedly", failure)
}
