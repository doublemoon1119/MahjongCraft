package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledHistoryReplayProjections

/**
 * 建立已註冊內建投影並完成凍結的歷史讀取 registry。
 *
 * @return 可供測試查詢且不可再註冊項目的 registry。
 */
internal fun buildTestHistoryReplayProjectionRegistry(): HistoryReplayProjectionRegistry = HistoryReplayProjectionRegistry().apply {
    this.registerBundledHistoryReplayProjections()
    freeze()
}
