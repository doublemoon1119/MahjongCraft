package com.doublemoon1119.mahjongcraft.platform.minecraft.showcase

/**
 * 展示使用的 ARGB 色盤。
 *
 * @property primary 主要顏色。
 * @property secondary 次要顏色。
 * @property accent 強調顏色。
 */
data class ShowcasePalette(
    val primary: Int,
    val secondary: Int,
    val accent: Int,
)
