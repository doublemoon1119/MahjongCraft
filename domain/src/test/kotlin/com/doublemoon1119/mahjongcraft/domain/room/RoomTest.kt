package com.doublemoon1119.mahjongcraft.domain.room

import com.doublemoon1119.mahjongcraft.testing.domain.fakes.config.FakeMahjongRuleConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.util.UUID

/**
 * 驗證 [Room] 的人數驗證與開局條件邏輯。
 */
class RoomTest {

    /**
     * 當玩家達到最大容量時，isFull 應返回 true。
     */
    @Test
    fun `test isFull returns true when players reach max capacity`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 3)
        val playerIds = setOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        val room = Room(
            id = UUID.randomUUID(),
            hostId = playerIds.first(),
            config = config,
            playerIds = playerIds
        )

        assertTrue(room.isFull)
    }

    /**
     * 當房間還有空間時，isFull 應返回 false。
     */
    @Test
    fun `test isFull returns false when room has space`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val playerIds = setOf(UUID.randomUUID(), UUID.randomUUID())
        val room = Room(
            id = UUID.randomUUID(),
            hostId = playerIds.first(),
            config = config,
            playerIds = playerIds
        )

        assertFalse(room.isFull)
    }

    /**
     * 當玩家人數在範圍內且所有玩家都準備時，canStart 應返回 true。
     */
    @Test
    fun `test canStart returns true when players are within range and all ready`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val playerIds = setOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        val room = Room(
            id = UUID.randomUUID(),
            hostId = playerIds.first(),
            config = config,
            playerIds = playerIds,
            readyPlayerIds = playerIds
        )

        assertTrue(room.canStart)
    }

    /**
     * 當玩家人數低於最小值時，canStart 應返回 false。
     */
    @Test
    fun `test canStart returns false when player count is below minimum`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val playerIds = setOf(UUID.randomUUID()) // 只有 1 人，低於最小值 2
        val room = Room(
            id = UUID.randomUUID(),
            hostId = playerIds.first(),
            config = config,
            playerIds = playerIds,
            readyPlayerIds = playerIds
        )

        assertFalse(room.canStart)
    }

    /**
     * 當有些玩家沒有準備時，canStart 應返回 false。
     */
    @Test
    fun `test canStart returns false when some players are not ready`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val player1 = UUID.randomUUID()
        val player2 = UUID.randomUUID()
        val player3 = UUID.randomUUID()
        val playerIds = setOf(player1, player2, player3)
        val readyPlayerIds = setOf(player1, player2) // player3 沒有準備
        val room = Room(
            id = UUID.randomUUID(),
            hostId = player1,
            config = config,
            playerIds = playerIds,
            readyPlayerIds = readyPlayerIds
        )

        assertFalse(room.canStart)
    }

    /**
     * 當準備玩家包含外部玩家（不在房間內的玩家）時，canStart 應返回 false。
     */
    @Test
    fun `test canStart returns false when ready players contains external player`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val player1 = UUID.randomUUID()
        val player2 = UUID.randomUUID()
        val externalPlayer = UUID.randomUUID() // 外部玩家，不在房間內
        val playerIds = setOf(player1, player2)
        val readyPlayerIds = setOf(player1, player2, externalPlayer) // 包含外部玩家
        val room = Room(
            id = UUID.randomUUID(),
            hostId = player1,
            config = config,
            playerIds = playerIds,
            readyPlayerIds = readyPlayerIds
        )

        assertFalse(room.canStart)
    }
}
