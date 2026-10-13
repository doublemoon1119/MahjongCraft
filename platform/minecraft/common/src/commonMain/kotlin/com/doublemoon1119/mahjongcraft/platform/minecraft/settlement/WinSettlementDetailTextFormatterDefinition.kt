package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 胡牌詳情文字格式器的登記描述。
 *
 * @property fieldId 詳情欄位的穩定識別碼。
 * @property formatter 將欄位語意值轉成顯示文字的格式器。
 */
data class WinSettlementDetailTextFormatterDefinition(
    val fieldId: String,
    val formatter: WinSettlementDetailTextFormatter,
)
