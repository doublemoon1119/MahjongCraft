package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayDiscard
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayRuleInformation
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryTileReference
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlinx.serialization.json.JsonElement
import org.koin.core.annotation.Single

/** 分類且強型別的歷史投影註冊表；由呼叫端註冊並明確凍結。 */
@Single
class HistoryReplayProjectionRegistry {
    /** 必要牌河轉換器。 */
    private val discardCodecs = linkedMapOf<String, HistoryReplayProjectionCodec<List<HistoryReplayDiscard>>>()

    /** 額外語意事實轉換器。 */
    private val factCodecs = linkedMapOf<String, HistoryReplayProjectionCodec<HistoryReplayFact>>()

    /** 擴充動作轉換器。 */
    private val actionCodecs = linkedMapOf<String, HistoryReplayProjectionCodec<HistoryReplayFact>>()

    /** 可選規則公開資訊轉換器。 */
    private val optionalRuleCodecs = linkedMapOf<String, HistoryReplayProjectionCodec<HistoryReplayRuleInformation>>()

    /** 從玩家規則狀態取出移出手牌的牌的轉換器。 */
    private val setAsideTileCodecs = linkedMapOf<String, HistoryReplayProjectionCodec<List<HistoryTileReference>>>()

    /** 註冊是否已禁止變更。 */
    private var frozen = false

    /** 取得各分類目前的穩定登記 key，供 extension bootstrap 診斷使用。 */
    val registrationKeys: HistoryReplayProjectionRegistrationKeys
        get() = HistoryReplayProjectionRegistrationKeys(
            discard = discardCodecs.keys.toSet(),
            fact = factCodecs.keys.toSet(),
            action = actionCodecs.keys.toSet(),
            optionalRule = optionalRuleCodecs.keys.toSet(),
            setAsideTiles = setAsideTileCodecs.keys.toSet(),
        )

    /**
     * 註冊必要牌河轉換。
     *
     * @param definition persistence typed envelope 種類與牌河轉換器。
     */
    fun register(definition: HistoryReplayProjectionDefinition.Discard) = register(discardCodecs, definition.typeKey, definition.codec)

    /**
     * 註冊額外事實轉換。
     *
     * @param definition 已保存事實種類與轉換器。
     */
    fun register(definition: HistoryReplayProjectionDefinition.Fact) = register(factCodecs, definition.typeKey, definition.codec, namespaced = false)

    /**
     * 註冊擴充動作的公開投影。
     *
     * @param definition 擴充動作種類與轉換器。
     */
    fun register(definition: HistoryReplayProjectionDefinition.Action) = register(actionCodecs, definition.typeKey, definition.codec)

    /**
     * 註冊可選規則公開資訊。
     *
     * @param definition persistence typed envelope 種類與公開資訊轉換器。
     */
    fun register(definition: HistoryReplayProjectionDefinition.OptionalRule) = register(optionalRuleCodecs, definition.typeKey, definition.codec)

    /**
     * 註冊從玩家規則狀態取出移出手牌的牌（例如三人日麻拔出的北）的轉換。
     *
     * @param definition 玩家規則狀態種類與牌參照轉換器。
     */
    fun register(definition: HistoryReplayProjectionDefinition.SetAsideTiles) = register(setAsideTileCodecs, definition.typeKey, definition.codec)

    /** 禁止後續註冊；不改變已註冊順序或內容。 */
    fun freeze() {
        frozen = true
    }

    /**
     * 解碼額外事實。
     *
     * @param typeKey 已保存種類。
     * @param value 內部事實。
     * @param context 受限上下文。
     * @return 已註冊結果；缺少轉換器為 null。
     */
    internal fun decodeFact(typeKey: String, value: JsonElement, context: HistoryReplayProjectionContext): HistoryReplayFact? = factCodecs[typeKey]?.decode(value, context)

    /**
     * 解碼擴充動作。
     *
     * @param typeKey 擴充動作種類。
     * @param value 私有 payload 的邊界內部資料。
     * @param context 受限上下文。
     * @return 公開讀模型；缺少轉換器為 null。
     */
    internal fun decodeAction(typeKey: String, value: JsonElement, context: HistoryReplayProjectionContext): HistoryReplayFact? = actionCodecs[typeKey]?.decode(value, context)

    /**
     * 解碼必要牌河。
     *
     * @param typeKey 牌河種類。
     * @param value 內部 payload。
     * @param context 受限上下文。
     * @return 牌河結果；缺少轉換器為 null。
     */
    internal fun decodeDiscard(typeKey: String, value: JsonElement, context: HistoryReplayProjectionContext): List<HistoryReplayDiscard>? = discardCodecs[typeKey]?.decode(value, context)

    /**
     * 解碼可選規則資料。
     *
     * @param typeKey 規則資料種類。
     * @param value 內部 payload。
     * @param context 受限上下文。
     * @return 公開資訊；缺少轉換器為 null。
     */
    internal fun decodeOptionalRule(typeKey: String, value: JsonElement, context: HistoryReplayProjectionContext): HistoryReplayRuleInformation? = optionalRuleCodecs[typeKey]?.decode(value, context)

    /**
     * 解碼移出手牌的牌。
     *
     * @param typeKey 玩家規則狀態種類。
     * @param value 內部 payload。
     * @param context 受限上下文。
     * @return 牌參照；缺少轉換器為 null。
     */
    internal fun decodeSetAsideTiles(typeKey: String, value: JsonElement, context: HistoryReplayProjectionContext): List<HistoryTileReference>? = setAsideTileCodecs[typeKey]?.decode(value, context)

    /**
     * 驗證並加入單一分類，不因失敗取代既有項目。
     *
     * @param T 分類中的讀模型。
     * @param target 強型別分類。
     * @param typeKey 穩定種類。
     * @param codec 轉換器。
     * @param namespaced 是否使用命名空間 ID；既有事實種類沿用保存格式中的短名稱。
     */
    private fun <T> register(target: MutableMap<String, HistoryReplayProjectionCodec<T>>, typeKey: String, codec: HistoryReplayProjectionCodec<T>, namespaced: Boolean = true) {
        check(!frozen) { "History replay projection registry is frozen" }
        require(typeKey.isNotBlank()) { "History replay projection type key must not be blank" }
        if (namespaced) NamespacedId.requireValid(typeKey) { "History replay projection type key must be a namespaced ID: $typeKey" }
        require(typeKey !in target) { "Duplicate history replay projection type key: $typeKey" }
        target[typeKey] = codec
    }
}
