package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricExtensionRegistries
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/**
 * Fabric 執行環境的 server graph。
 *
 * Dedicated server 與 client 程序內的 integrated server 都需要此 module。它包含 [modServerModule] 與 [fabricCommonModule]，
 * 另外提供 [FabricExtensionRegistries]：把既有 registry 組成 Fabric 擴充登記、診斷快照與凍結使用的集合，不建立第二份實例。
 */
val fabricServerModule = module {
    includes(modServerModule, fabricCommonModule)

    single<FabricExtensionRegistries>()
}
