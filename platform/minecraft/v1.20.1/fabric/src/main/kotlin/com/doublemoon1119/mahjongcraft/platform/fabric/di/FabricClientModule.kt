package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.flow.client.di.FlowClientModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

/**
 * Fabric client 使用的 Koin 定義。
 *
 * 此 module 只由 Minecraft client graph 載入，可掃描 HUD、renderer 與其他 client-only adapter；
 * dedicated server graph 不會 include 此 module。
 */
@Module(includes = [FlowClientModule::class, FabricCommonModule::class])
@ComponentScan("com.doublemoon1119.mahjongcraft.platform.fabric.client")
class FabricClientModule
