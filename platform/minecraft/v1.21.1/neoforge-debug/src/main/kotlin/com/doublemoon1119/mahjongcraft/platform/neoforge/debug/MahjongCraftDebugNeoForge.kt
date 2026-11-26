package com.doublemoon1119.mahjongcraft.platform.neoforge.debug

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.DebugClientCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.AutotestClientRunner
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.autotestClientSteps
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestSelection
import com.doublemoon1119.mahjongcraft.platform.neoforge.loader.NeoForgeClientEvents
import com.doublemoon1119.mahjongcraft.platform.neoforge.loader.NeoForgeLoaderPlatform
import net.neoforged.fml.common.Mod
import org.koin.core.context.GlobalContext

/**
 * debug 模組的 NeoForge 入口。
 *
 * - 在用戶端登記用戶端的 debug 指令。
 * - 開發執行帶自動測試設定時，掛上自動測試。
 * - 伺服器端的 debug 服務與指令，由本體以 `neoforge.mods.toml` 宣告的開發用入口載入，不經過這裡。
 */
@Mod(DEBUG_MOD_ID)
object MahjongCraftDebugNeoForge {
    init {
        if (NeoForgeLoaderPlatform.isClient) {
            NeoForgeClientEvents.onRegisterClientCommand(DebugClientCommand(configStore = { GlobalContext.get().get() }))
            AutotestSelection.current?.let { AutotestClientRunner(it, autotestClientSteps).register(NeoForgeClientEvents) }
        }
    }
}

/** debug 工具的 mod ID。 */
private const val DEBUG_MOD_ID = "mahjongcraft_debug"
