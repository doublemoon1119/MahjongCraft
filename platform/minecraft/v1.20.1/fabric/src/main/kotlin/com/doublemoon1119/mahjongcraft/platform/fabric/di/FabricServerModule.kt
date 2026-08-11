package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.flow.common.di.FlowCommonModule
import com.doublemoon1119.mahjongcraft.flow.server.di.FlowServerModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Minecraft server 流程使用的 Fabric adapter 定義。
 *
 * Dedicated server 與 client 程序內的 integrated server 都需要此 module。它包含 [FlowServerModule]、
 * [FabricCommonModule] 與所有 server-side Fabric adapter，但不掃描 `platform.fabric.client`，因此不會把
 * HUD、renderer 或其他 Minecraft client-only 類別加入 server graph。
 */
@Module(includes = [FlowCommonModule::class, FlowServerModule::class, FabricCommonModule::class])
@ComponentScan(
    "com.doublemoon1119.mahjongcraft.platform.fabric.concurrency",
    "com.doublemoon1119.mahjongcraft.platform.fabric.event",
    "com.doublemoon1119.mahjongcraft.platform.fabric.game",
    "com.doublemoon1119.mahjongcraft.platform.fabric.metadata",
    "com.doublemoon1119.mahjongcraft.platform.fabric.network",
    "com.doublemoon1119.mahjongcraft.platform.fabric.persistence",
    "com.doublemoon1119.mahjongcraft.platform.fabric.player",
    "com.doublemoon1119.mahjongcraft.platform.fabric.room",
    "com.doublemoon1119.mahjongcraft.platform.fabric.server",
    "com.doublemoon1119.mahjongcraft.platform.fabric.table",
)
class FabricServerModule {
    /** 提供目前使用預設值的伺服器政策；後續由磁碟 config adapter 取代。 */
    @Single
    fun provideMinecraftServerConfig(): MinecraftServerConfig = MinecraftServerConfig()

    /** 建立目前 server session 使用的桌子位置索引。 */
    @Single
    fun provideTableLocationRegistry(): TableLocationRegistry = TableLocationRegistry()
}
