package com.doublemoon1119.mahjongcraft.platform.minecraft.room

/** 供內建與第三方規則登記設定呈現定義的凍結式 registry。 */
interface GameConfigPresentationRegistry {
    /** 目前已登記規則模組 ID 的快照。 */
    val registrationKeys: Set<String> get() = ruleModuleIds.toSet()

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 目前已登記的規則模組 ID。 */
    val ruleModuleIds: Set<String>

    /** 登記設定呈現定義。 */
    fun register(definition: GameConfigPresentationDefinition)

    /** 查詢設定呈現定義。 */
    fun find(ruleModuleId: String): GameConfigPresentationDefinition?

    /** 凍結 registry。 */
    fun freeze()
}
