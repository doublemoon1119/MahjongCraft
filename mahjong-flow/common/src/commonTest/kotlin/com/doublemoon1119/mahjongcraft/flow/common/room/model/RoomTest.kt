package com.doublemoon1119.mahjongcraft.flow.common.room.model

import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class RoomTest {

    @Test
    fun `test isFull returns true when players reach max capacity`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 3)
        val playerIds = setOf(Uuid.random(), Uuid.random(), Uuid.random())
        val room = Room(
            id = Uuid.random(),
            hostId = playerIds.first(),
            config = config,
            playerIds = playerIds,
        )

        assertTrue(room.isFull, "Room should be full when player count equals max capacity.")
    }

    @Test
    fun `test isFull returns false when room has space`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val playerIds = setOf(Uuid.random(), Uuid.random())
        val room = Room(
            id = Uuid.random(),
            hostId = playerIds.first(),
            config = config,
            playerIds = playerIds,
        )

        assertFalse(room.isFull, "Room should not be full when there is remaining capacity.")
    }

    @Test
    fun `test canStart returns true when other players are ready and host is not in ready list`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val host = Uuid.random()
        val player2 = Uuid.random()
        val player3 = Uuid.random()

        val playerIds = setOf(host, player2, player3)
        val readyPlayerIds = setOf(player2, player3)

        val room = Room(
            id = Uuid.random(),
            hostId = host,
            config = config,
            playerIds = playerIds,
            readyPlayerIds = readyPlayerIds,
        )

        assertTrue(room.canStart, "Room should be able to start when all players except the host are ready.")
    }

    @Test
    fun `test canStart returns false when player count is below minimum`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val host = Uuid.random()
        val playerIds = setOf(host)

        val room = Room(
            id = Uuid.random(),
            hostId = host,
            config = config,
            playerIds = playerIds,
            readyPlayerIds = emptySet(),
        )

        assertFalse(room.canStart, "Room should not start if the number of players is below the minimum required.")
    }

    @Test
    fun `test canStart returns false when some non-host players are not ready`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val host = Uuid.random()
        val player2 = Uuid.random()
        val player3 = Uuid.random()

        val playerIds = setOf(host, player2, player3)
        val readyPlayerIds = setOf(player2)

        val room = Room(
            id = Uuid.random(),
            hostId = host,
            config = config,
            playerIds = playerIds,
            readyPlayerIds = readyPlayerIds,
        )

        assertFalse(room.canStart, "Room should not start if any player (other than host) is not ready.")
    }

    @Test
    fun `test canStart returns false when ready players contains external player`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val host = Uuid.random()
        val player2 = Uuid.random()
        val externalPlayer = Uuid.random()

        val playerIds = setOf(host, player2)
        val readyPlayerIds = setOf(player2, externalPlayer)

        val room = Room(
            id = Uuid.random(),
            hostId = host,
            config = config,
            playerIds = playerIds,
            readyPlayerIds = readyPlayerIds,
        )

        assertFalse(room.canStart, "Room should not start if ready list contains players not present in the room.")
    }

    @Test
    fun `test isAi returns true for players in ai set`() {
        val aiId = Uuid.random()
        val room = Room(
            id = Uuid.random(),
            hostId = Uuid.random(),
            config = FakeMahjongRuleConfig(),
            aiPlayerIds = setOf(aiId),
        )

        assertTrue(room.isAi(aiId), "isAi should return true for ID in aiPlayerIds.")
        assertFalse(room.isAi(Uuid.random()), "isAi should return false for unknown ID.")
    }

    @Test
    fun `test humanPlayerIds filters out ai players`() {
        val hostId = Uuid.random()
        val humanId = Uuid.random()
        val aiId = Uuid.random()

        val room = Room(
            id = Uuid.random(),
            hostId = hostId,
            config = FakeMahjongRuleConfig(),
            playerIds = setOf(hostId, humanId, aiId),
            aiPlayerIds = setOf(aiId),
        )

        val humanPlayers = room.humanPlayerIds
        assertEquals(2, humanPlayers.size, "Should only contain 2 human players.")
        assertTrue(humanPlayers.contains(hostId))
        assertTrue(humanPlayers.contains(humanId))
        assertFalse(humanPlayers.contains(aiId), "AI player should be filtered out.")
    }
}
