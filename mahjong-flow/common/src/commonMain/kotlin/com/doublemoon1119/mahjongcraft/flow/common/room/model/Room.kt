package com.doublemoon1119.mahjongcraft.flow.common.room.model

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import kotlin.uuid.Uuid

data class Room(
    val id: Uuid,
    val hostId: Uuid,
    val config: MahjongRuleConfig,
    val playerIds: Set<Uuid> = emptySet(),
    val readyPlayerIds: Set<Uuid> = emptySet(),
    val aiPlayerIds: Set<Uuid> = emptySet()
) {
    private val allowedRange: IntRange get() = config.minPlayers..config.maxPlayers

    val isFull: Boolean get() = playerIds.size >= config.maxPlayers

    val canStart: Boolean
        get() {
            if (playerIds.size !in allowedRange) return false

            val otherPlayers = playerIds - hostId

            return readyPlayerIds.size == otherPlayers.size &&
                    readyPlayerIds.containsAll(otherPlayers)
        }

    val humanPlayerIds: Set<Uuid> get() = playerIds - aiPlayerIds

    fun isAi(playerId: Uuid): Boolean = aiPlayerIds.contains(playerId)
}
