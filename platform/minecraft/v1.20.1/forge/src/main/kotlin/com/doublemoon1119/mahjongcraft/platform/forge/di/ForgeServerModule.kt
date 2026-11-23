package com.doublemoon1119.mahjongcraft.platform.forge.di

import com.doublemoon1119.mahjongcraft.platform.forge.loader.ForgeBlockUseInterception
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.di.modServerModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.BlockUseInterception
import org.koin.dsl.module

/**
 * Forge 執行環境的 server graph。
 *
 * Dedicated server 與 client 程序內的 integrated server 都需要此 module。它包含 [modServerModule] 與 [forgeCommonModule]，
 * 另外提供 Forge 的方塊使用攔截。
 */
val forgeServerModule = module {
    includes(modServerModule, forgeCommonModule)
    single<BlockUseInterception> { ForgeBlockUseInterception }
}
