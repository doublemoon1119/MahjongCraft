package com.doublemoon1119.mahjongcraft.flow.common.room.model

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import kotlin.uuid.Uuid

data class RoomSnapshot(
    val id: Uuid,
    val hostId: Uuid,
    val config: MahjongRuleConfig,
    val playerIds: Set<Uuid>,
    val readyPlayerIds: Set<Uuid>,
    val aiPlayerIds: Set<Uuid>,
    val canStart: Boolean,
    val isHost: Boolean,
    val isInRoom: Boolean
)

fun Room.toSnapshot(observerId: Uuid): RoomSnapshot {
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
