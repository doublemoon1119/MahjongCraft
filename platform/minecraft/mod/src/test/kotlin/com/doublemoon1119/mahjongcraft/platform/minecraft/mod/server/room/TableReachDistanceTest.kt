package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.room

import net.minecraft.world.level.GameType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/** 驗證選桌時依遊戲模式決定的可觸距離。 */
class TableReachDistanceTest {
    /** 生存、冒險、創造模式都使用玩家與方塊互動的距離。 */
    @Test
    fun `non spectator modes use the player's block interaction reach`() {
        listOf(GameType.SURVIVAL, GameType.ADVENTURE, GameType.CREATIVE).forEach { gameType ->
            assertEquals(7.25, tableReachDistance(gameType) { 7.25 }, "$gameType")
        }
    }

    /** 旁觀模式不限距離，也不讀取玩家的互動距離。 */
    @Test
    fun `spectators reach every table`() {
        assertEquals(Double.MAX_VALUE, tableReachDistance(GameType.SPECTATOR) { fail("Spectators do not read the block reach") })
    }
}
