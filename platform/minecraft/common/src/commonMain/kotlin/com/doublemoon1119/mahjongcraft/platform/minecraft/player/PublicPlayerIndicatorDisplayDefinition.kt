package com.doublemoon1119.mahjongcraft.platform.minecraft.player

/**
 * 將玩家公開標記 ID 綁定到顯示文字與顏色。
 *
 * @property id 登記項目的穩定識別碼。
 * @property display 對應的呈現描述。
 */
data class PublicPlayerIndicatorDisplayDefinition(
    val id: String,
    val display: PublicPlayerIndicatorDisplay,
)
