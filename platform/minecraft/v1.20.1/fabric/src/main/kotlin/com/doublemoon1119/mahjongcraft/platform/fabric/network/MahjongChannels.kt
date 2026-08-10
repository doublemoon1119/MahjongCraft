package com.doublemoon1119.mahjongcraft.platform.fabric.network

import com.doublemoon1119.mahjongcraft.flow.network.dto.GameCommandEnvelopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.GameSnapshotSyncPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.GameUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.RoomSnapshotSyncPayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.RoomUpdatePayloadDto

/** `mahjongcraft:` 命名空間下實際使用的命令、事件更新與主動快照同步頻道。 */
object MahjongChannels {
    val gameCommand = C2SChannel("game_command", GameCommandEnvelopeDto.serializer())
    val gameUpdate = S2CChannel("game_update", GameUpdatePayloadDto.serializer())
    val roomUpdate = S2CChannel("room_update", RoomUpdatePayloadDto.serializer())
    val gameSnapshot = S2CChannel("game_snapshot", GameSnapshotSyncPayloadDto.serializer())
    val roomSnapshot = S2CChannel("room_snapshot", RoomSnapshotSyncPayloadDto.serializer())
}
