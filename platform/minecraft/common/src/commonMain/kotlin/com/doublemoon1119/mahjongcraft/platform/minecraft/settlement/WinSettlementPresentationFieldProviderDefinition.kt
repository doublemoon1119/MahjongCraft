package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 胡牌結算欄位 provider 的登記描述。
 *
 * @property fieldId provider 提供值的欄位識別碼。
 * @property provider 由結算快照建立欄位值的 provider。
 */
data class WinSettlementPresentationFieldProviderDefinition(
    val fieldId: PresentationFieldId,
    val provider: WinSettlementPresentationFieldProvider,
)
