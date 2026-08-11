package com.doublemoon1119.mahjongcraft.platform.fabric.di

import org.koin.core.annotation.KoinApplication

/**
 * Dedicated server 使用的 Koin application。
 *
 * 此 graph 只由 [FabricServerModule] 組裝共用定義、flow-server 與 Fabric server adapter，不登記
 * [FabricClientModule] 的 client state。
 */
@KoinApplication(modules = [FabricServerModule::class])
class MahjongCraftServerApp
