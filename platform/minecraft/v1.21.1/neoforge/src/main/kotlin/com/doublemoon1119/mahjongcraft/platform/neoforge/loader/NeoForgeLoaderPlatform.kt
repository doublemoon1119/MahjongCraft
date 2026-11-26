package com.doublemoon1119.mahjongcraft.platform.neoforge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.LoaderPlatform
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLLoader
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Path

/** 以 NeoForge 的 Mod Loader 提供的資訊實作 [LoaderPlatform]。 */
object NeoForgeLoaderPlatform : LoaderPlatform {
    override val name: String = "NeoForge"

    override val isClient: Boolean
        get() = FMLLoader.getDist().isClient

    override val isDevelopment: Boolean
        get() = !FMLLoader.isProduction()

    override val configDirectory: Path
        get() = FMLPaths.CONFIGDIR.get()

    /** 讀取 Gradle 展開至 `neoforge.mods.toml` 後由 NeoForge 解析的版本。 */
    override val modVersion: String
        get() = ModList.get()
            .getModContainerById(MinecraftModMetadata.MOD_ID)
            .orElseThrow { IllegalStateException("NeoForge metadata for ${MinecraftModMetadata.MOD_ID} is unavailable.") }
            .modInfo
            .version
            .toString()

    override fun isModLoaded(modId: String): Boolean = ModList.get().isLoaded(modId)
}
