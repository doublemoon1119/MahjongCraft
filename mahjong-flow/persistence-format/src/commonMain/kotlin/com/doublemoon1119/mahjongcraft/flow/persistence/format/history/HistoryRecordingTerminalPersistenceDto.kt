package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import kotlinx.serialization.Serializable

/**
 * 已結束歷史場次的持久化終點證據。
 *
 * @property endedAtEpochMillis 場次離開權威狀態集合的 UTC 毫秒時間戳，不一定等於正常終局時間。
 * @property completed 場次是否正常完成整場對局。
 * @property venueId 原場地 UUID 字串。
 */
@Serializable
data class HistoryRecordingTerminalPersistenceDto(
    val endedAtEpochMillis: Long,
    val completed: Boolean,
    val venueId: String,
)
