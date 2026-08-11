package com.doublemoon1119.mahjongcraft.platform.fabric.di

import org.koin.core.annotation.KoinApplication

/**
 * Minecraft client 程序使用的 Koin application。
 *
 * Minecraft client 可啟動 integrated server，因此此 graph 同時包含 [FabricServerModule] 與
 * [FabricClientModule]；連線到 dedicated server 時 server-side single 維持延遲建立，不會建立第二個 Koin
 * container。
 */
@KoinApplication(modules = [FabricServerModule::class, FabricClientModule::class])
class MahjongCraftClientApp
