package com.doublemoon1119.mahjongcraft.platform.forge.debug

import com.doublemoon1119.mahjongcraft.platform.forge.loader.ForgeClientEvents
import com.doublemoon1119.mahjongcraft.platform.forge.loader.ForgeLoaderPlatform
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.DebugClientCommand
import net.minecraftforge.fml.common.Mod
import org.koin.core.context.GlobalContext

/**
 * debug 模組的 Forge 入口：在用戶端登記用戶端的 debug 指令。
 *
 * 伺服器端的 debug 服務與指令由本體以 `mods.toml` 宣告的開發用入口載入，不經過這裡。
 */
@Mod(DEBUG_MOD_ID)
object MahjongCraftDebugForge {
    init {
        if (ForgeLoaderPlatform.isClient) {
            ForgeClientEvents.onRegisterClientCommand(DebugClientCommand(configStore = { GlobalContext.get().get() }))
        }
    }
}

/** debug 模組的 mod ID，與 `mods.toml` 相同。 */
private const val DEBUG_MOD_ID = "mahjongcraft_debug"
