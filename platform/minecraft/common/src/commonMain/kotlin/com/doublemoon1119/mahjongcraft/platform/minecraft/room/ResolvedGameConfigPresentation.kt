package com.doublemoon1119.mahjongcraft.platform.minecraft.room

/**
 * 一次解析後供 RoomScreen 與聊天 hover 共用的規則設定呈現。
 *
 * @property ruleModuleId 規則模組 ID。
 * @property definition 規則模組的設定呈現定義；未知規則時為 null。
 * @property valuesByFieldId 依欄位 ID 索引的目前設定值。
 * @property variant 設定目前的玩法；未知規則或識別欄位不符合任何玩法時為 null。
 */
data class ResolvedGameConfigPresentation(
    val ruleModuleId: String,
    val definition: GameConfigPresentationDefinition?,
    val valuesByFieldId: Map<String, GameConfigPresentationValue>,
    val variant: GameConfigVariantDefinition?,
)
