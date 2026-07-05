package com.doublemoon1119.mahjongcraft.flow.common.room.model

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import java.util.*

data class RoomSnapshot(
    val id: UUID,
    val hostId: UUID,
    val config: MahjongRuleConfig,
    val playerIds: Set<UUID>,
    val readyPlayerIds: Set<UUID>,
    val aiPlayerIds: Set<UUID>,
    val canStart: Boolean,
    val isHost: Boolean,
    val isInRoom: Boolean
)

fun Room.toSnapshot(observerId: UUID): RoomSnapshot {
    return RoomSnapshot(
        id = id,
        hostId = hostId,
        config = config,
        playerIds = playerIds,
        readyPlayerIds = readyPlayerIds,
        aiPlayerIds = aiPlayerIds,
        canStart = canStart,
        isHost = observerId == hostId,
        isInRoom = playerIds.contains(observerId)
    )
}
