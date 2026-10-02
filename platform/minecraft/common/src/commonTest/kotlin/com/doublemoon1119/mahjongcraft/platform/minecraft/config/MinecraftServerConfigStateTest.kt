package com.doublemoon1119.mahjongcraft.platform.minecraft.config

import kotlin.test.Test
import kotlin.test.assertEquals

/** [MinecraftServerConfigState] 的有效設定替換測試。 */
class MinecraftServerConfigStateTest {
    /** 完整替換後應讀到新的不可變設定 snapshot。 */
    @Test
    fun `test replace updates current config`() {
        val state = MinecraftServerConfigState()
        val replacement = MinecraftServerConfig(
            disconnectedPlayerPolicy = DisconnectedPlayerPolicy.LEAVE_IMMEDIATELY,
        )

        state.replace(replacement)

        assertEquals(replacement, state.current)
        assertEquals(replacement, state.updates.value)
    }

    /** 重設會同時更新即時讀值與設定同步來源。 */
    @Test
    fun `test reset publishes default config`() {
        val state = MinecraftServerConfigState(MinecraftServerConfig(history = MinecraftHistoryConfig(queryMinimumIntervalMilliseconds = 1_000)))
        state.reset()
        assertEquals(MinecraftServerConfig(), state.current)
        assertEquals(state.current, state.updates.value)
    }
}
