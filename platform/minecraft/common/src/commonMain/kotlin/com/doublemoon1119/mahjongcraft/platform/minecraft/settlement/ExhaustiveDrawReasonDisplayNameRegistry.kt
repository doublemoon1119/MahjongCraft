package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys

/** 將完整流局原因 ID 映射至 Minecraft translation key。 */
interface ExhaustiveDrawReasonDisplayNameRegistry {
    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 全部已註冊的完整 reason ID。 */
    val reasonIds: Set<String>

    /** 登記 reason ID 的本地化名稱。 */
    fun register(reasonId: String, translationKey: String)

    /** 查詢本地化名稱；未知 ID 回傳 null。 */
    fun find(reasonId: String): String?

    /** 凍結 registry。 */
    fun freeze()
}

/** [ExhaustiveDrawReasonDisplayNameRegistry] 的記憶體實作。 */
class ExhaustiveDrawReasonDisplayNameRegistryImpl : ExhaustiveDrawReasonDisplayNameRegistry {
    private val translations = mutableMapOf<String, String>()
    override var isFrozen: Boolean = false
        private set
    override val reasonIds: Set<String> get() = translations.keys

    override fun register(reasonId: String, translationKey: String) {
        check(!isFrozen) { "Exhaustive-draw reason display-name registry is frozen" }
        require(':' in reasonId) { "Exhaustive-draw reason ID must be namespaced: $reasonId" }
        require(translationKey.isNotBlank()) { "Translation key must not be blank" }
        require(translations.putIfAbsent(reasonId, translationKey) == null) { "Duplicate exhaustive-draw reason: $reasonId" }
    }

    override fun find(reasonId: String): String? = translations[reasonId]

    override fun freeze() {
        isFrozen = true
    }
}

/** 登記內建日麻流局原因。 */
fun ExhaustiveDrawReasonDisplayNameRegistry.registerBuiltInRiichiReasons() {
    register(RiichiExhaustiveDrawReason.Normal.id, MinecraftMessageKeys.EXHAUSTIVE_DRAW_REASON_NORMAL)
    register(RiichiExhaustiveDrawReason.KyuushuKyuuhai.id, MinecraftMessageKeys.GAME_ACTION_KYUUSHU_KYUUHAI)
    register(RiichiExhaustiveDrawReason.SuufonRenda.id, MinecraftMessageKeys.GAME_ACTION_SUUFON_RENDA)
    register(RiichiExhaustiveDrawReason.SuukanNagare.id, MinecraftMessageKeys.GAME_ACTION_SUUKAN_NAGARE)
    register(RiichiExhaustiveDrawReason.SuuchaRiichi.id, MinecraftMessageKeys.GAME_ACTION_SUUCHA_RIICHI)
    register(RiichiExhaustiveDrawReason.SanchaHou.id, MinecraftMessageKeys.GAME_ACTION_SANCHA_HOU)
}
