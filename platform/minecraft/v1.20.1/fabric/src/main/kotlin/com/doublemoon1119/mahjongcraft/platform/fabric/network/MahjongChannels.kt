package com.doublemoon1119.mahjongcraft.platform.fabric.network

import com.doublemoon1119.mahjongcraft.flow.dto.GameCommandEnvelopeDto
import com.doublemoon1119.mahjongcraft.flow.dto.GameUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.dto.RoomUpdatePayloadDto

/** `mahjongcraft:` 命名空間下實際用到的三條頻道。 */
object MahjongChannels {
    val gameCommand = C2SChannel("game_command", GameCommandEnvelopeDto.serializer())
    val gameUpdate = S2CChannel("game_update", GameUpdatePayloadDto.serializer())
    val roomUpdate = S2CChannel("room_update", RoomUpdatePayloadDto.serializer())
}
