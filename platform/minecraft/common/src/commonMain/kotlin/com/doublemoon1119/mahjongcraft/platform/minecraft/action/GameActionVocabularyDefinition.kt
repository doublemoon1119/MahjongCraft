package com.doublemoon1119.mahjongcraft.platform.minecraft.action

/**
 * GameActionVocabulary registry 的規則專屬登記描述。
 *
 * @property ruleModuleId 規則模組的穩定識別碼。
 * @property actionId 動作的穩定識別碼。
 * @property vocabulary 該規則使用的顯示用語。
 */
data class GameActionVocabularyDefinition(
    val ruleModuleId: String,
    val actionId: String,
    val vocabulary: GameActionVocabulary,
)
