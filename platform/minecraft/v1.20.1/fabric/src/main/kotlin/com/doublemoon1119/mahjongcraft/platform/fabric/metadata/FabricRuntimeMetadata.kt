package com.doublemoon1119.mahjongcraft.platform.fabric.metadata

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.SharedConstants
import org.koin.core.annotation.Single

/** 從 Fabric 與 Minecraft runtime 提供初始化紀錄需要的實際版本資訊。 */
@Single
class FabricRuntimeMetadata {
    /** 讀取 Gradle 展開至 `fabric.mod.json` 後由 Fabric Loader 解析的 mod 版本。 */
    val modVersion: String
        get() = FabricLoader.getInstance()
            .getModContainer(MinecraftModMetadata.MOD_ID)
            .orElseThrow {
                IllegalStateException("Fabric metadata for ${MinecraftModMetadata.MOD_ID} is unavailable.")
            }
            .metadata
            .version
            .friendlyString

    /** 讀取目前執行中的 Minecraft 版本。 */
    val minecraftVersion: String
        get() = SharedConstants.getGameVersion().name

    /** 組成包含 mod、loader 與 Minecraft 版本的初始化訊息。 */
    fun initializationMessage(): String = "${MinecraftModMetadata.MOD_NAME} $modVersion (Fabric, Minecraft $minecraftVersion) initialized."
}
