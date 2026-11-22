package com.doublemoon1119.mahjongcraft.platform.fabric.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

/** 以 Fabric API 的用戶端事件實作 [ClientEvents]。 */
object FabricClientEvents : ClientEvents {
    override fun onRegisterClientCommand(command: ClientCommand) {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(command.build<FabricClientCommandSource>())
        }
    }
}
