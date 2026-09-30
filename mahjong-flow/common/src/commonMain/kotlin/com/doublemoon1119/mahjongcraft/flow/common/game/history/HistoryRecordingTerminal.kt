package com.doublemoon1119.mahjongcraft.flow.common.game.history

import kotlin.uuid.Uuid

/** 已離開權威遊戲集合的歷史場次終點證據。
 *
 * @property endedAtEpochMillis 場次離開權威集合的 UTC 毫秒時間戳。
 * @property completed 場次離開時是否已完成整場對局。
 * @property tableId 原牌桌 UUID。
 */
data class HistoryRecordingTerminal(
    val endedAtEpochMillis: Long,
    val completed: Boolean,
    val tableId: Uuid,
)
