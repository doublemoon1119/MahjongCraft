package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/** 一般牌：只有右上角紅色文字。 */
internal fun redText(text: String): TileLabel = TileLabel(
    topLeft = null,
    topRight = TileLabelText(text, TileLabelColor.RED),
)

/** 牌面本身印刷成紅色的牌（紅中，以及例如日麻的赤五）：右上角文字改用黑色維持對比。 */
internal fun blackTextOnRedTile(text: String): TileLabel = TileLabel(
    topLeft = null,
    topRight = TileLabelText(text, TileLabelColor.BLACK),
)
