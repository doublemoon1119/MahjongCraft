package com.doublemoon1119.mahjongcraft.platform.forge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.LoaderPlatform
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLLoader
import net.minecraftforge.fml.loading.FMLPaths
import java.nio.file.Path

/** 以 Forge Mod Loader 提供的資訊實作 [LoaderPlatform]。 */
object ForgeLoaderPlatform : LoaderPlatform {
    override val name: String = "Forge"

    override val isClient: Boolean
        get() = FMLLoader.getDist().isClient

    override val isDevelopment: Boolean
        get() = !FMLLoader.isProduction()

    override val configDirectory: Path
        get() = FMLPaths.CONFIGDIR.get()

    /** 讀取 Gradle 展開至 `mods.toml` 後由 Forge 解析的版本。 */
    override val modVersion: String
        get() = ModList.get()
            .getModContainerById(MinecraftModMetadata.MOD_ID)
            .orElseThrow { IllegalStateException("Forge metadata for ${MinecraftModMetadata.MOD_ID} is unavailable.") }
            .modInfo
            .version
            .toString()

    override fun isModLoaded(modId: String): Boolean = ModList.get().isLoaded(modId)
}
