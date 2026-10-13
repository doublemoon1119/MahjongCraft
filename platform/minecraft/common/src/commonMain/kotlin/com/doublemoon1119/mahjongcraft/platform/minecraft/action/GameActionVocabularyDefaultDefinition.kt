package com.doublemoon1119.mahjongcraft.platform.minecraft.action

/**
 * GameActionVocabulary registry 的中立預設登記描述。
 *
 * @property actionId 動作的穩定識別碼。
 * @property vocabulary 所有規則共用的顯示用語。
 */
data class GameActionVocabularyDefaultDefinition(
    val actionId: String,
    val vocabulary: GameActionVocabulary,
)
