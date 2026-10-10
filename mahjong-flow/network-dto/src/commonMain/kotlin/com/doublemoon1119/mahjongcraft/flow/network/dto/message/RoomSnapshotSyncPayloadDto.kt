package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.RoomSnapshotDto
import kotlinx.serialization.Serializable

/**
 * 沒有伴隨房間事件、單純重新同步最新 [RoomSnapshot] 時使用的 S2C payload。
 *
 * @property roomId 房間 UUID 的字串表示。
 * @property snapshot 指定 observer 當下可見的完整房間快照。
 */
@Serializable
data class RoomSnapshotSyncPayloadDto(
    val roomId: String,
    val snapshot: RoomSnapshotDto,
)
