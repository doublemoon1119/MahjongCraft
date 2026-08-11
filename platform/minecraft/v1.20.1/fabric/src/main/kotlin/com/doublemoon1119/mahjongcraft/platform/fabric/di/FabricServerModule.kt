package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.flow.server.di.FlowServerModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.di.MinecraftServerModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

/**
 * Minecraft server 流程使用的 Fabric adapter 定義。
 *
 * Dedicated server 與 client 程序內的 integrated server 都需要此 module。它包含 [FlowServerModule]、
 * [MinecraftServerModule]、[FabricCommonModule] 與所有 server-side Fabric adapter，但不掃描
 * `platform.fabric.client`，因此不會把 HUD、renderer 或其他 Minecraft client-only 類別加入 server graph。
 */
@Module(
    includes = [
        FlowServerModule::class,
        MinecraftServerModule::class,
        FabricCommonModule::class,
    ],
)
@ComponentScan("com.doublemoon1119.mahjongcraft.platform.fabric.server")
class FabricServerModule
