package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 單一角落顯示的標籤文字與顏色。
 *
 * @property text 要顯示的文字。
 * @property color 文字顏色。
 */
data class TileLabelText(
    val text: String,
    val color: TileLabelColor,
)
