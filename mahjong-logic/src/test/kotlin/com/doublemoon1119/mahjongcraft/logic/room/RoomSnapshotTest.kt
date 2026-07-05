package com.doublemoon1119.mahjongcraft.logic.room

import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 驗證 [Room.toSnapshot] 的觀察者識別邏輯。
 */
class RoomSnapshotTest {

    /**
     * 測試 toSnapshot 能正確識別觀察者是否為房主。
     */
    @Test
    fun `test toSnapshot identifies host correctly`() {
        val config = FakeMahjongRuleConfig()
        val hostId = UUID.randomUUID()
        val playerIds = setOf(hostId, UUID.randomUUID())
        val room = Room(
            id = UUID.randomUUID(),
            hostId = hostId,
            config = config,
            playerIds = playerIds,
            readyPlayerIds = playerIds
        )

        val snapshot = room.toSnapshot(hostId)

        assertTrue(snapshot.isHost)
    }

    /**
     * 測試 toSnapshot 能正確識別觀察者是否為房間成員。
     */
    @Test
    fun `test toSnapshot identifies room member correctly`() {
        val config = FakeMahjongRuleConfig()
        val hostId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        val playerIds = setOf(hostId, memberId)
        val room = Room(
            id = UUID.randomUUID(),
            hostId = hostId,
            config = config,
            playerIds = playerIds,
            readyPlayerIds = playerIds
        )

        val snapshot = room.toSnapshot(memberId)

        assertTrue(snapshot.isInRoom)
        assertFalse(snapshot.isHost)
    }

    /**
     * 測試 toSnapshot 能正確識別外部觀察者（非房主、非成員）。
     */
    @Test
    fun `test toSnapshot identifies external observer correctly`() {
        val config = FakeMahjongRuleConfig()
        val hostId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        val externalId = UUID.randomUUID()
        val playerIds = setOf(hostId, memberId)
        val room = Room(
            id = UUID.randomUUID(),
            hostId = hostId,
            config = config,
            playerIds = playerIds,
            readyPlayerIds = playerIds
        )

        val snapshot = room.toSnapshot(externalId)

        assertFalse(snapshot.isHost)
        assertFalse(snapshot.isInRoom)
    }

    /**
     * 驗證轉換後的快照是否正確包含了 AI 玩家的清單。
     */
    @Test
    fun `test toSnapshot contains ai player info`() {
        val hostId = UUID.randomUUID()
        val aiId = UUID.randomUUID()
        val room = Room(
            id = UUID.randomUUID(),
            hostId = hostId,
            config = FakeMahjongRuleConfig(),
            playerIds = setOf(hostId, aiId),
            aiPlayerIds = setOf(aiId)
        )

        val snapshot = room.toSnapshot(hostId)

        assertEquals(room.aiPlayerIds, snapshot.aiPlayerIds, "Snapshot should contain the same AI player IDs.")
        assertTrue(snapshot.aiPlayerIds.contains(aiId), "AI player should be present in snapshot.")
    }
}
