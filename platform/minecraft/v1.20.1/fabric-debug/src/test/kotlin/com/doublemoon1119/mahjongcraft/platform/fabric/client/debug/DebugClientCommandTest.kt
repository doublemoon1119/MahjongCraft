package com.doublemoon1119.mahjongcraft.platform.fabric.client.debug

import com.doublemoon1119.mahjongcraft.platform.fabric.client.CLIENT_COMMAND_ROOT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** 驗證客戶端 debug 指令掛在客戶端指令根節點的 `debug` 底下。 */
class DebugClientCommandTest {
    /** 指令路徑為 `debug hovered_text client_config`，且只有這一條。 */
    @Test
    fun `hovered text preview is mounted under the client debug node`() {
        val root = DebugClientCommand(configStore = { error("The command tree does not read the config") }).build<Any>().build()

        assertEquals(CLIENT_COMMAND_ROOT, root.name)
        val debug = assertNotNull(root.getChild(DebugClientCommand.DEBUG_SUBCOMMAND))
        val hovered = assertNotNull(debug.getChild(DebugClientCommand.HOVERED_TEXT_SUBCOMMAND))
        assertEquals(listOf(DebugClientCommand.CLIENT_CONFIG_ARGUMENT), hovered.children.map { it.name })
        assertEquals(1, root.children.size)
    }
}
