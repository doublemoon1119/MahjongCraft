package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 建立一般牌使用的右上角紅色標籤。
 *
 * @param text 要顯示的文字。
 * @return 只有右上角紅色文字的標籤。
 */
internal fun redText(text: String): TileLabel = TileLabel(
    topLeft = null,
    topRight = TileLabelText(text, TileLabelColor.RED),
)

/**
 * 為紅色牌面建立右上角黑色標籤，以維持對比。
 *
 * @param text 要顯示的文字。
 * @return 只有右上角黑色文字的標籤。
 */
internal fun blackTextOnRedTile(text: String): TileLabel = TileLabel(
    topLeft = null,
    topRight = TileLabelText(text, TileLabelColor.BLACK),
)
