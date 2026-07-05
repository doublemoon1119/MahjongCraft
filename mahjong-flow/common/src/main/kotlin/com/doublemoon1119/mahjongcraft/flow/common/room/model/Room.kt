package com.doublemoon1119.mahjongcraft.flow.common.room.model

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import java.util.*

data class Room(
    val id: UUID,
    val hostId: UUID,
    val config: MahjongRuleConfig,
    val playerIds: Set<UUID> = emptySet(),
    val readyPlayerIds: Set<UUID> = emptySet(),
    val aiPlayerIds: Set<UUID> = emptySet()
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

    val humanPlayerIds: Set<UUID> get() = playerIds - aiPlayerIds

    fun isAi(playerId: UUID): Boolean = aiPlayerIds.contains(playerId)
}
