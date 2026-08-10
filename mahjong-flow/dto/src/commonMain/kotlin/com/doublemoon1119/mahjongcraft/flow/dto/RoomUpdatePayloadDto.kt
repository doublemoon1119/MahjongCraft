package com.doublemoon1119.mahjongcraft.flow.dto

import kotlinx.serialization.Serializable

/**
 * `mahjongcraft:room_update` S2C 頻道用的事件種類——把 [com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher]
 * 的四個方法收斂成同一條頻道能傳的一個 sealed 型別，純粹是線路用的封裝，不動既有的
 * `RoomEventPublisher` 介面本身。
 */
@Serializable
sealed interface RoomUpdateEventDto {
    @Serializable data class Join(val joinedPlayerId: String, val reason: JoinReasonDto) : RoomUpdateEventDto

    @Serializable data class Leave(val leftPlayerId: String, val reason: LeaveReasonDto) : RoomUpdateEventDto

    @Serializable data class Ready(val readyPlayerId: String, val isReady: Boolean) : RoomUpdateEventDto

    @Serializable data class ConfigChanged(val newConfig: MahjongRuleConfigDto) : RoomUpdateEventDto
}

/**
 * `mahjongcraft:room_update` S2C 頻道的線路信封，跟 [GameUpdatePayloadDto] 同樣的理由——事件跟
 * 該次事件觸發後的最新房間快照包在同一個封包送出。
 */
@Serializable
data class RoomUpdatePayloadDto(
    val roomId: String,
    val event: RoomUpdateEventDto,
    val snapshot: RoomSnapshotDto,
)
