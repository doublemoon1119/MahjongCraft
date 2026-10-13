package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

/**
 * DecisionStatusDisplayName registry 的規則專屬登記描述。
 *
 * @property ruleModuleId 規則模組的穩定識別碼。
 * @property statusId 狀態的穩定識別碼。
 * @property translationKey 狀態名稱的翻譯 key。
 */
data class DecisionStatusDisplayNameDefinition(
    val ruleModuleId: String,
    val statusId: String,
    val translationKey: String,
)
