package com.doublemoon1119.mahjongcraft.platform.fabric.client.debug

import com.doublemoon1119.mahjongcraft.platform.fabric.loader.FabricClientEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.DebugClientCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.AutotestClientRunner
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.autotestClientSteps
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestSelection
import net.fabricmc.api.ClientModInitializer
import org.koin.core.context.GlobalContext

/**
 * debug 模組的客戶端進入點。
 *
 * - 登記客戶端的 debug 指令。
 * - 開發執行帶自動測試設定時，掛上自動測試。
 */
class DebugClientEntrypoint : ClientModInitializer {
    override fun onInitializeClient() {
        val command = DebugClientCommand(configStore = { GlobalContext.get().get() })
        FabricClientEvents.onRegisterClientCommand(command)
        AutotestSelection.current?.let { AutotestClientRunner(it, autotestClientSteps).register(FabricClientEvents) }
    }
}
