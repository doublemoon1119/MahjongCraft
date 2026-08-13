package com.doublemoon1119.mahjongcraft.platform.minecraft.config

import kotlin.test.Test
import kotlin.test.assertEquals

/** [MinecraftServerConfig] 預設生命週期政策的單元測試。 */
class MinecraftServerConfigTest {
    /** 預設值立即移除等待階段的斷線玩家，並拒絕破壞使用中的麻將桌。 */
    @Test
    fun `test defaults remove disconnected waiting players and deny breaking occupied tables`() {
        val config = MinecraftServerConfig()

        assertEquals(DisconnectedPlayerPolicy.LEAVE_IMMEDIATELY, config.disconnectedPlayerPolicy)
        assertEquals(TableBreakPolicy.DENY_WHILE_OCCUPIED, config.tableBreakPolicy)
        assertEquals(OrphanedTablePolicy.REMOVE_ALL, config.orphanedTablePolicy)
        assertEquals(30, config.disconnectedPlayerTimeoutSeconds)
        assertEquals(true, config.mahjongTilePhysicalCollisionEnabled)
    }

    /** 各破壞政策應依 Room／Game 占用狀態產生固定結果。 */
    @Test
    fun `test table break policies distinguish room and game occupancy`() {
        assertEquals(true, TableBreakPolicy.DENY_WHILE_OCCUPIED.allowsTableBreak(false, false))
        assertEquals(false, TableBreakPolicy.DENY_WHILE_OCCUPIED.allowsTableBreak(true, false))
        assertEquals(false, TableBreakPolicy.DENY_WHILE_OCCUPIED.allowsTableBreak(false, true))
        assertEquals(true, TableBreakPolicy.ALLOW_WAITING_ROOM_ONLY.allowsTableBreak(true, false))
        assertEquals(false, TableBreakPolicy.ALLOW_WAITING_ROOM_ONLY.allowsTableBreak(false, true))
        assertEquals(true, TableBreakPolicy.ALLOW_AND_TERMINATE.allowsTableBreak(false, true))
    }
}
