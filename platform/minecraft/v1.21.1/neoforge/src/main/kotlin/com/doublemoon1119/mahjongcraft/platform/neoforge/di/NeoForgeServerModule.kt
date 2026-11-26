package com.doublemoon1119.mahjongcraft.platform.neoforge.di

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.di.modServerModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.BlockUseInterception
import com.doublemoon1119.mahjongcraft.platform.neoforge.loader.NeoForgeBlockUseInterception
import org.koin.dsl.module

/**
 * NeoForge 執行環境的 server graph。
 *
 * Dedicated server 與 client 程序內的 integrated server 都需要此 module。它包含 [modServerModule] 與 [neoForgeCommonModule]，
 * 另外提供 NeoForge 的方塊使用攔截。
 */
val neoForgeServerModule = module {
    includes(modServerModule, neoForgeCommonModule)
    single<BlockUseInterception> { NeoForgeBlockUseInterception }
}
