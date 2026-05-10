package com.doublemoon1119.mahjongcraft.domain.room

import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.domain.config.FakeMahjongRuleConfig
import java.util.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [Room] 領域模型的單元測試。
 *
 * 驗證房間的人數限制、容量判斷以及基於房主權限與成員準備狀態的開局邏輯。
 */
class RoomTest {

    /**
     * 測試當玩家數量達到 [MahjongRuleConfig.maxPlayers] 時，[Room.isFull] 應返回 true。
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

        assertTrue(room.isFull, "Room should be full when player count equals max capacity.")
    }

    /**
     * 測試當玩家數量少於 [MahjongRuleConfig.maxPlayers] 時，[Room.isFull] 應返回 false。
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

        assertFalse(room.isFull, "Room should not be full when there is remaining capacity.")
    }

    /**
     * 測試當人數達標且房主以外的所有玩家皆已準備時，[Room.canStart] 應返回 true。
     *
     * 注意：房主不需要出現在 readyPlayerIds 中。
     */
    @Test
    fun `test canStart returns true when other players are ready and host is not in ready list`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val host = UUID.randomUUID()
        val player2 = UUID.randomUUID()
        val player3 = UUID.randomUUID()

        val playerIds = setOf(host, player2, player3)
        val readyPlayerIds = setOf(player2, player3) // 僅非房主玩家準備

        val room = Room(
            id = UUID.randomUUID(),
            hostId = host,
            config = config,
            playerIds = playerIds,
            readyPlayerIds = readyPlayerIds
        )

        assertTrue(room.canStart, "Room should be able to start when all players except the host are ready.")
    }

    /**
     * 測試當玩家總人數低於 [MahjongRuleConfig.minPlayers] 時，即使全體準備也無法開局。
     */
    @Test
    fun `test canStart returns false when player count is below minimum`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val host = UUID.randomUUID()
        val playerIds = setOf(host) // 只有 1 人，低於最小值 2

        val room = Room(
            id = UUID.randomUUID(),
            hostId = host,
            config = config,
            playerIds = playerIds,
            readyPlayerIds = emptySet()
        )

        assertFalse(room.canStart, "Room should not start if the number of players is below the minimum required.")
    }

    /**
     * 測試當存在至少一位非房主玩家未準備時，[Room.canStart] 應返回 false。
     */
    @Test
    fun `test canStart returns false when some non-host players are not ready`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val host = UUID.randomUUID()
        val player2 = UUID.randomUUID()
        val player3 = UUID.randomUUID()

        val playerIds = setOf(host, player2, player3)
        val readyPlayerIds = setOf(player2) // player3 未準備

        val room = Room(
            id = UUID.randomUUID(),
            hostId = host,
            config = config,
            playerIds = playerIds,
            readyPlayerIds = readyPlayerIds
        )

        assertFalse(room.canStart, "Room should not start if any player (other than host) is not ready.")
    }

    /**
     * 測試當準備名單包含不存在於房間內的玩家時，[Room.canStart] 應返回 false。
     */
    @Test
    fun `test canStart returns false when ready players contains external player`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val host = UUID.randomUUID()
        val player2 = UUID.randomUUID()
        val externalPlayer = UUID.randomUUID()

        val playerIds = setOf(host, player2)
        val readyPlayerIds = setOf(player2, externalPlayer)

        val room = Room(
            id = UUID.randomUUID(),
            hostId = host,
            config = config,
            playerIds = playerIds,
            readyPlayerIds = readyPlayerIds
        )

        assertFalse(room.canStart, "Room should not start if ready list contains players not present in the room.")
    }
}