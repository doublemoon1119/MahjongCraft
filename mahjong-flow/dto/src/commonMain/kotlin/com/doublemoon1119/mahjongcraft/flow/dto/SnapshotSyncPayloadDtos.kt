package com.doublemoon1119.mahjongcraft.flow.dto

import kotlinx.serialization.Serializable

/** 沒有伴隨房間事件、單純重新同步最新 observer 快照時使用的 S2C payload。 */
@Serializable
data class RoomSnapshotSyncPayloadDto(
    /** 房間 UUID 的字串表示。 */
    val roomId: String,
    /** 指定 observer 當下可見的完整房間快照。 */
    val snapshot: RoomSnapshotDto,
)

/** 沒有伴隨遊戲動作、單純重新同步最新 observer 快照時使用的 S2C payload。 */
@Serializable
data class GameSnapshotSyncPayloadDto(
    /** 遊戲 UUID 的字串表示。 */
    val gameId: String,
    /** 指定 observer 當下可見的完整遊戲快照。 */
    val snapshot: TableStateSnapshotDto,
)
