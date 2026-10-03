package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.registerBuiltInHistoryReplayProjections

/** 建立已載入內建投影並完成凍結的歷史讀取 registry 測試 fixture。 */
internal fun buildTestHistoryReplayProjectionRegistry(): HistoryReplayProjectionRegistry = HistoryReplayProjectionRegistry().apply {
    registerBuiltInHistoryReplayProjections(this)
    freeze()
}
