package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

/**
 * DecisionStatusDisplayName registry 的中立預設登記描述。
 *
 * @property statusId 狀態的穩定識別碼。
 * @property translationKey 狀態名稱的翻譯 key。
 */
data class DecisionStatusDisplayNameDefaultDefinition(
    val statusId: String,
    val translationKey: String,
)
