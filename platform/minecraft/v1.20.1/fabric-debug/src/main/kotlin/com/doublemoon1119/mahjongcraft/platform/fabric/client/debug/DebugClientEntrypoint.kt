package com.doublemoon1119.mahjongcraft.platform.fabric.client.debug

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import org.koin.core.context.GlobalContext

/** debug 模組的客戶端進入點：登記客戶端的 debug 指令。 */
class DebugClientEntrypoint : ClientModInitializer {
    override fun onInitializeClient() {
        val command = DebugClientCommand(configStore = { GlobalContext.get().get() })
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ -> dispatcher.register(command.build()) }
    }
}
