package com.doublemoon1119.mahjongcraft.platform.minecraft.action

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/**
 * 一個動作在某個規則下的顯示用語。
 *
 * 同一個動作在不同規則可能有不同說法（例如榮和與胡牌、捨牌與丟牌），因此用語屬於規則，不寫在呈現程式裡。
 *
 * @property labelKey 操作卡與標題用的短名稱 translation key，不帶參數。
 * @property messageKey 聊天訊息用的 translation key，可帶牌面參數；預設沿用 [labelKey]。
 * @property order 操作卡由左至右的順序；`null` 代表排在所有已登記的動作之後，並維持原始相對順序。
 * @property descriptionKey 說明這個動作做什麼的 translation key，顯示在操作卡的提示框與選牌提示；`null` 代表不需要說明，
 * 例如吃、碰等基本動作。
 * @property cardBodyKey 操作卡中央說明的 translation key；`null` 代表不顯示中央說明。
 * @property cardHintKey 操作卡按鈕附近提示的 translation key；`null` 代表不顯示額外提示。
 */
data class GameActionVocabulary(
    val labelKey: String,
    val messageKey: String = labelKey,
    val order: Int? = null,
    val descriptionKey: String? = null,
    val cardBodyKey: String? = null,
    val cardHintKey: String? = null,
) {
    init {
        require(labelKey.isNotBlank()) { "Game action label key must not be blank" }
        require(messageKey.isNotBlank()) { "Game action message key must not be blank" }
        require(descriptionKey == null || descriptionKey.isNotBlank()) { "Game action description key must not be blank" }
        require(cardBodyKey == null || cardBodyKey.isNotBlank()) { "Game action card body key must not be blank" }
        require(cardHintKey == null || cardHintKey.isNotBlank()) { "Game action card hint key must not be blank" }
    }
}

/**
 * 取得動作在用語 registry 中使用的穩定 ID。
 *
 * 核心動作為 [BuiltInGameActionIds] 的固定 ID；流局宣告用流局原因 ID，規則擴充動作用動作自己的 ID，
 * 兩者都由規則決定。
 *
 * @receiver 要轉成顯示用語識別碼的動作。
 * @return 用語查詢的穩定 ID；沒有對應核心種類時回傳未知動作 ID。
 */
fun GameAction.vocabularyActionId(): String = when (this) {
    is GameAction.Chi -> BuiltInGameActionIds.CHI
    is GameAction.Pon -> BuiltInGameActionIds.PON
    is GameAction.Kan -> when (type) {
        GameAction.KanType.OPEN_KAN -> BuiltInGameActionIds.KAN_OPEN
        GameAction.KanType.CLOSED_KAN -> BuiltInGameActionIds.KAN_CLOSED
        GameAction.KanType.ADDED_KAN -> BuiltInGameActionIds.KAN_ADDED
    }
    is GameAction.Ron -> BuiltInGameActionIds.RON
    GameAction.Tsumo -> BuiltInGameActionIds.TSUMO
    GameAction.Pass -> BuiltInGameActionIds.PASS
    is GameAction.Discard -> BuiltInGameActionIds.DISCARD
    is GameAction.ExhaustiveDraw -> reason.id
    is GameAction.Extension -> value.id
    else -> BuiltInGameActionIds.UNKNOWN
}

/** HUD 操作卡短名稱的 translation key 前綴。 */
internal const val HUD_ACTION_PREFIX: String = MinecraftModMetadata.MOD_ID + ".hud.action."
