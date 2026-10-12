package com.doublemoon1119.mahjongcraft.platform.minecraft.sound

import com.doublemoon1119.mahjongcraft.logic.base.GameAction

/** 管理規則模組動作與 Minecraft 語音呈現的凍結式 registry。 */
interface GameActionSoundPresentationRegistry {
    /** 目前已登記規則模組與動作組合的穩定 key 快照。 */
    val registrationKeys: Set<String> get() = definitionKeys.mapTo(mutableSetOf()) { (ruleId, actionId) -> "$ruleId/$actionId" }

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 已登記的「規則模組／動作」組合。 */
    val definitionKeys: Set<Pair<String, String>>

    /** 登記一項宣告式動作語音。 */
    fun register(definition: GameActionSoundDefinition)

    /** 解析指定規則模組與權威動作；沒有對應時保持靜音。 */
    fun find(ruleModuleId: String, action: GameAction): GameActionSoundPresentation?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}

/**
 * 取得動作在 Minecraft 宣告式聲音 registry 中使用的穩定 ID。
 *
 * @return 對應的穩定聲音 ID；不支援語音的動作為 `null`。
 */
fun GameAction.soundPresentationId(): String? = when (this) {
    is GameAction.Chi -> BuiltInGameActionSoundIds.CHII
    is GameAction.Pon -> BuiltInGameActionSoundIds.PON
    is GameAction.Kan -> BuiltInGameActionSoundIds.KAN
    is GameAction.Ron -> BuiltInGameActionSoundIds.RON
    GameAction.Tsumo -> BuiltInGameActionSoundIds.TSUMO
    is GameAction.Extension -> value.id
    else -> null
}
