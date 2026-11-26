package com.doublemoon1119.mahjongcraft.platform.minecraft.history

/**
 * 將牌河標記 ID 綁定到歷史牌面使用的呈現方式。
 *
 * @property markerId 牌河標記的穩定識別碼。
 * @property display 對應的呈現描述。
 */
data class HistoryDiscardMarkerDisplayDefinition(
    val markerId: String,
    val display: HistoryDiscardMarkerDisplay,
)
