package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyDefaultDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyDefinition

/** 擴充登記動作用語時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface GameActionVocabularyRegistrar {
    /** 目前已登記組合的穩定 key 快照，格式為 `規則模組 ID/動作 ID`，中立預設以 [DEFAULT_KEY] 表示。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記所有規則共用的中立預設。
     *
     * @param definition 中立預設的完整登記描述。
     */
    fun register(definition: GameActionVocabularyDefaultDefinition)

    /**
     * 登記指定規則模組專屬的用語，優先於同一動作的中立預設。
     *
     * @param definition 規則專屬用語的完整登記描述。
     */
    fun register(definition: GameActionVocabularyDefinition)
}
