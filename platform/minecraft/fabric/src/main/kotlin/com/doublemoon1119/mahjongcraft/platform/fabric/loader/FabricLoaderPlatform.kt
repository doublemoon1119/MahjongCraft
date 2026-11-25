package com.doublemoon1119.mahjongcraft.platform.fabric.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.LoaderPlatform
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path

/** 以 Fabric Loader 提供的資訊實作 [LoaderPlatform]。 */
object FabricLoaderPlatform : LoaderPlatform {
    override val name: String = "Fabric"

    override val isClient: Boolean
        get() = FabricLoader.getInstance().environmentType == EnvType.CLIENT

    override val isDevelopment: Boolean
        get() = FabricLoader.getInstance().isDevelopmentEnvironment

    override val configDirectory: Path
        get() = FabricLoader.getInstance().configDir

    /** 讀取 Gradle 展開至 `fabric.mod.json` 後由 Fabric Loader 解析的版本。 */
    override val modVersion: String
        get() = FabricLoader.getInstance()
            .getModContainer(MinecraftModMetadata.MOD_ID)
            .orElseThrow { IllegalStateException("Fabric metadata for ${MinecraftModMetadata.MOD_ID} is unavailable.") }
            .metadata
            .version
            .friendlyString

    override fun isModLoaded(modId: String): Boolean = FabricLoader.getInstance().isModLoaded(modId)
}
