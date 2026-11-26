package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.environment

import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.LoaderPlatform

/**
 * 依載入器回報的資訊判斷執行環境的 [MinecraftEnvironment]。
 *
 * @property platform 載入器提供的執行環境資訊。
 */
class LoaderMinecraftEnvironment(private val platform: LoaderPlatform) : MinecraftEnvironment {
    override val isDevelopment: Boolean
        get() = platform.isDevelopment
}
