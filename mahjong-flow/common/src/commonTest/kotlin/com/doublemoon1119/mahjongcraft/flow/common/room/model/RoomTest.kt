package com.doublemoon1119.mahjongcraft.flow.common.room.model

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證房間容量、規則選擇狀態與玩家分類。 */
class RoomTest {

    /** 玩家人數達到上限時房間已滿。 */
    @Test
    fun `test isFull returns true when players reach max capacity`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 3)
        val playerIds = listOf(Uuid.random(), Uuid.random(), Uuid.random())
        val room = Room(
            id = Uuid.random(),
            hostId = playerIds.first(),
            gameConfig = GameConfig(config),
            playerIds = playerIds,
        )

        assertTrue(room.isFull, "Room should be full when player count equals max capacity.")
    }

    /** 玩家人數未達上限時房間尚有空位。 */
    @Test
    fun `test isFull returns false when room has space`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val playerIds = listOf(Uuid.random(), Uuid.random())
        val room = Room(
            id = Uuid.random(),
            hostId = playerIds.first(),
            gameConfig = GameConfig(config),
            playerIds = playerIds,
        )

        assertFalse(room.isFull, "Room should not be full when there is remaining capacity.")
    }

    /** 房間人數足夠且非房主玩家皆已準備時可開局。 */
    @Test
    fun `test canStart returns true when other players are ready and host is not in ready list`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val host = Uuid.random()
        val player2 = Uuid.random()
        val player3 = Uuid.random()

        val playerIds = listOf(host, player2, player3)
        val readyPlayerIds = listOf(player2, player3)

        val room = Room(
            id = Uuid.random(),
            hostId = host,
            gameConfig = GameConfig(config),
            playerIds = playerIds,
            readyPlayerIds = readyPlayerIds,
        )

        assertTrue(room.canStart, "Room should be able to start when all players except the host are ready.")
    }

    /** 玩家人數低於規則下限時人數無效。 */
    @Test
    fun `test isPlayerCountValid returns false when player count is below minimum`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val host = Uuid.random()

        val room = Room(
            id = Uuid.random(),
            hostId = host,
            gameConfig = GameConfig(config),
            playerIds = listOf(host),
        )

        assertFalse(room.isPlayerCountValid, "Player count should be invalid when below the configured minimum.")
    }

    /** 玩家人數落在規則允許範圍時人數有效。 */
    @Test
    fun `test isPlayerCountValid returns true when player count is within range`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val host = Uuid.random()
        val playerIds = listOf(host, Uuid.random())

        val room = Room(
            id = Uuid.random(),
            hostId = host,
            gameConfig = GameConfig(config),
            playerIds = playerIds,
        )

        assertTrue(room.isPlayerCountValid, "Player count should be valid when within the configured range.")
    }

    /** 玩家人數低於規則下限時不可開局。 */
    @Test
    fun `test canStart returns false when player count is below minimum`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val host = Uuid.random()
        val playerIds = listOf(host)

        val room = Room(
            id = Uuid.random(),
            hostId = host,
            gameConfig = GameConfig(config),
            playerIds = playerIds,
            readyPlayerIds = emptyList(),
        )

        assertFalse(room.canStart, "Room should not start if the number of players is below the minimum required.")
    }

    /** 任一非房主玩家尚未準備時不可開局。 */
    @Test
    fun `test canStart returns false when some non-host players are not ready`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val host = Uuid.random()
        val player2 = Uuid.random()
        val player3 = Uuid.random()

        val playerIds = listOf(host, player2, player3)
        val readyPlayerIds = listOf(player2)

        val room = Room(
            id = Uuid.random(),
            hostId = host,
            gameConfig = GameConfig(config),
            playerIds = playerIds,
            readyPlayerIds = readyPlayerIds,
        )

        assertFalse(room.canStart, "Room should not start if any player (other than host) is not ready.")
    }

    /** 準備清單包含房間外玩家時不可開局。 */
    @Test
    fun `test canStart returns false when ready players contains external player`() {
        val config = FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 4)
        val host = Uuid.random()
        val player2 = Uuid.random()
        val externalPlayer = Uuid.random()

        val playerIds = listOf(host, player2)
        val readyPlayerIds = listOf(player2, externalPlayer)

        val room = Room(
            id = Uuid.random(),
            hostId = host,
            gameConfig = GameConfig(config),
            playerIds = playerIds,
            readyPlayerIds = readyPlayerIds,
        )

        assertFalse(room.canStart, "Room should not start if ready list contains players not present in the room.")
    }

    /** AI 策略集合中的玩家會被識別為 AI。 */
    @Test
    fun `test isAi returns true for players in ai set`() {
        val aiId = Uuid.random()
        val room = Room(
            id = Uuid.random(),
            hostId = Uuid.random(),
            gameConfig = GameConfig(FakeMahjongRuleConfig()),
            aiPlayerStrategyKeys = mapOf(aiId to "mahjongcraft:random"),
        )

        assertTrue(room.isAi(aiId), "isAi should return true for ID in aiPlayerIds.")
        assertFalse(room.isAi(Uuid.random()), "isAi should return false for unknown ID.")
    }

    /** 人類玩家清單會排除 AI 玩家。 */
    @Test
    fun `test humanPlayerIds filters out ai players`() {
        val hostId = Uuid.random()
        val humanId = Uuid.random()
        val aiId = Uuid.random()

        val room = Room(
            id = Uuid.random(),
            hostId = hostId,
            gameConfig = GameConfig(FakeMahjongRuleConfig()),
            playerIds = listOf(hostId, humanId, aiId),
            aiPlayerStrategyKeys = mapOf(aiId to "mahjongcraft:random"),
        )

        val humanPlayers = room.humanPlayerIds
        assertEquals(2, humanPlayers.size, "Should only contain 2 human players.")
        assertTrue(humanPlayers.contains(hostId))
        assertTrue(humanPlayers.contains(humanId))
        assertFalse(humanPlayers.contains(aiId), "AI player should be filtered out.")
    }
}
