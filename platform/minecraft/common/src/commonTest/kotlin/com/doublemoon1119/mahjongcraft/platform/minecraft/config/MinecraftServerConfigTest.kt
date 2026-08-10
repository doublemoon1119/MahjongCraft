package com.doublemoon1119.mahjongcraft.platform.minecraft.config

import kotlin.test.Test
import kotlin.test.assertEquals

/** [MinecraftServerConfig] 預設生命週期政策的單元測試。 */
class MinecraftServerConfigTest {
    /** 預設值採保守策略，不會因斷線或破壞請求直接遺失 Room／Game。 */
    @Test
    fun `test defaults preserve disconnected seats and deny breaking occupied tables`() {
        val config = MinecraftServerConfig()

        assertEquals(DisconnectedPlayerPolicy.KEEP_SEAT, config.disconnectedPlayerPolicy)
        assertEquals(TableBreakPolicy.DENY_WHILE_OCCUPIED, config.tableBreakPolicy)
        assertEquals(300, config.disconnectedPlayerTimeoutSeconds)
    }
}
