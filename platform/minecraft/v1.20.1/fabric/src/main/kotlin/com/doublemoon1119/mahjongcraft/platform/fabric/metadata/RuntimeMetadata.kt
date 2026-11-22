package com.doublemoon1119.mahjongcraft.platform.fabric.metadata

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.LoaderPlatform
import net.minecraft.SharedConstants

/**
 * 從載入器與 Minecraft runtime 提供初始化紀錄需要的實際版本資訊。
 *
 * @property platform 提供 mod 版本與載入器名稱的載入器資訊。
 */
class RuntimeMetadata(private val platform: LoaderPlatform) {
    /** 載入器回報的 mod 版本。 */
    val modVersion: String
        get() = platform.modVersion

    /** 讀取目前執行中的 Minecraft 版本。 */
    val minecraftVersion: String
        get() = SharedConstants.getCurrentVersion().name

    /** 組成包含 mod、loader 與 Minecraft 版本的初始化訊息。 */
    fun initializationMessage(): String = "${MinecraftModMetadata.MOD_NAME} $modVersion (${platform.name}, Minecraft $minecraftVersion) initialized."
}
