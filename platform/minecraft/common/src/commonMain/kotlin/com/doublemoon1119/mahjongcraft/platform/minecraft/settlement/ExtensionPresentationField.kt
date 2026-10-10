package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 不使用字串 Map 的單一 extension 顯示欄位。
 *
 * @property id 完整欄位 ID。
 * @property value 欄位值。
 */
data class ExtensionPresentationField(
    val id: PresentationFieldId,
    val value: PresentationValue,
)
