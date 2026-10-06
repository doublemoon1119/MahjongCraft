package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.BundledRiichiMinecraftExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.BundledTaiwanMinecraftExtension

/** 隨 MahjongCraft 發布、一律啟用的 Minecraft 呈現 extension。 */
object BundledMinecraftMahjongExtensions {
    /** 依登記順序排列的內建規則呈現 extension；平台應排在第三方 extension 之前登記。 */
    val all: List<MinecraftMahjongExtension> = listOf(BundledRiichiMinecraftExtension, BundledTaiwanMinecraftExtension)
}
