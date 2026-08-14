package com.doublemoon1119.mahjongcraft.flow.common.room.model

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class RoomSnapshotTest {

    @Test
    fun `test toSnapshot identifies host correctly`() {
        val config = FakeMahjongRuleConfig()
        val hostId = Uuid.random()
        val playerIds = listOf(hostId, Uuid.random())
        val room = Room(
            id = Uuid.random(),
            hostId = hostId,
            gameConfig = GameConfig(config),
            playerIds = playerIds,
            readyPlayerIds = playerIds,
        )

        val snapshot = room.toSnapshot(hostId)

        assertTrue(snapshot.isHost)
    }

    @Test
    fun `test toSnapshot identifies room member correctly`() {
        val config = FakeMahjongRuleConfig()
        val hostId = Uuid.random()
        val memberId = Uuid.random()
        val playerIds = listOf(hostId, memberId)
        val room = Room(
            id = Uuid.random(),
            hostId = hostId,
            gameConfig = GameConfig(config),
            playerIds = playerIds,
            readyPlayerIds = playerIds,
        )

        val snapshot = room.toSnapshot(memberId)

        assertTrue(snapshot.isInRoom)
        assertFalse(snapshot.isHost)
    }

    @Test
    fun `test toSnapshot identifies external observer correctly`() {
        val config = FakeMahjongRuleConfig()
        val hostId = Uuid.random()
        val memberId = Uuid.random()
        val externalId = Uuid.random()
        val playerIds = listOf(hostId, memberId)
        val room = Room(
            id = Uuid.random(),
            hostId = hostId,
            gameConfig = GameConfig(config),
            playerIds = playerIds,
            readyPlayerIds = playerIds,
        )

        val snapshot = room.toSnapshot(externalId)

        assertFalse(snapshot.isHost)
        assertFalse(snapshot.isInRoom)
    }

    @Test
    fun `test toSnapshot contains ai player info`() {
        val hostId = Uuid.random()
        val aiId = Uuid.random()
        val room = Room(
            id = Uuid.random(),
            hostId = hostId,
            gameConfig = GameConfig(FakeMahjongRuleConfig()),
            playerIds = listOf(hostId, aiId),
            aiPlayerStrategyKeys = mapOf(aiId to "random"),
        )

        val snapshot = room.toSnapshot(hostId)

        assertEquals(room.aiPlayerIds, snapshot.aiPlayerIds, "Snapshot should contain the same AI player IDs.")
        assertTrue(snapshot.aiPlayerIds.contains(aiId), "AI player should be present in snapshot.")
    }
}
