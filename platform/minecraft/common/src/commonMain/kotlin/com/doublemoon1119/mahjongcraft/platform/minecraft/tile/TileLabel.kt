package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/** 牌面角落標籤文字顏色，只保留渲染層需要對應到實際色碼的兩種語意值。 */
enum class TileLabelColor {
    BLACK,
    RED,
}

/** 單一角落顯示的標籤文字與顏色。 */
data class TileLabelText(val text: String, val color: TileLabelColor)

/** 一張牌左上角／右上角的標籤內容；任一角落為 `null` 代表該角落不顯示文字。 */
data class TileLabel(val topLeft: TileLabelText?, val topRight: TileLabelText?)
