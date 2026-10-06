package com.doublemoon1119.mahjongcraft.bundled

import com.doublemoon1119.mahjongcraft.extension.MahjongExtension

/** 隨 MahjongCraft 發布、一律啟用的 extension。 */
object BundledMahjongExtensions {
    /** 依登記順序排列的內建規則 extension；平台應排在第三方 extension 之前登記。 */
    val all: List<MahjongExtension> = listOf(BundledRiichiExtension, BundledTaiwanExtension)
}
