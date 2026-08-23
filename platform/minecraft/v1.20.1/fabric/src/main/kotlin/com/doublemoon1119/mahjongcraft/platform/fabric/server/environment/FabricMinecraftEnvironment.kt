package com.doublemoon1119.mahjongcraft.platform.fabric.server.environment

import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import net.fabricmc.loader.api.FabricLoader
import org.koin.core.annotation.Single

/** [MinecraftEnvironment] 的 Fabric 實作，直接委託 [FabricLoader.isDevelopmentEnvironment]。 */
@Single(binds = [MinecraftEnvironment::class])
class FabricMinecraftEnvironment : MinecraftEnvironment {
    override val isDevelopment: Boolean
        get() = FabricLoader.getInstance().isDevelopmentEnvironment
}
