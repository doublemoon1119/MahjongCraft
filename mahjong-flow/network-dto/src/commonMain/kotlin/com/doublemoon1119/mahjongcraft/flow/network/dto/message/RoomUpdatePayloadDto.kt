package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.RoomSnapshotDto
import kotlinx.serialization.Serializable

/**
 * `mahjongcraft:room_update` S2C 頻道的網路信封，跟 [GameUpdatePayloadDto] 同樣的理由——事件跟
 * 該次事件觸發後的最新房間快照包在同一個封包送出。
 *
 * @property roomId 受更新影響的房間 UUID 字串。
 * @property event 發生的房間事件。
 * @property snapshot 事件套用後的房間快照。
 */
@Serializable
data class RoomUpdatePayloadDto(
    val roomId: String,
    val event: RoomUpdateEventDto,
    val snapshot: RoomSnapshotDto,
)
