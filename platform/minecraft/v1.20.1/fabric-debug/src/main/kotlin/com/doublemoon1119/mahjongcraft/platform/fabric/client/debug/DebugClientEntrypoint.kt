package com.doublemoon1119.mahjongcraft.platform.fabric.client.debug

import com.doublemoon1119.mahjongcraft.platform.fabric.loader.FabricClientEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.DebugClientCommand
import net.fabricmc.api.ClientModInitializer
import org.koin.core.context.GlobalContext

/** debug 模組的客戶端進入點：登記客戶端的 debug 指令。 */
class DebugClientEntrypoint : ClientModInitializer {
    override fun onInitializeClient() {
        val command = DebugClientCommand(configStore = { GlobalContext.get().get() })
        FabricClientEvents.onRegisterClientCommand(command)
    }
}
