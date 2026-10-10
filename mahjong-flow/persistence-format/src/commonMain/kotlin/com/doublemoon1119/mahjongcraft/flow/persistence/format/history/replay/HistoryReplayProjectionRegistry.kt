package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayDiscard
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayRuleInformation
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryTileReference
import kotlinx.serialization.json.JsonElement

/** 分類且強型別的歷史投影註冊表；由呼叫端註冊並明確凍結。 */
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
     * @param typeKey persistence typed envelope 的種類。
     * @param codec 牌河轉換器。
     */
    fun registerDiscard(typeKey: String, codec: HistoryReplayProjectionCodec<List<HistoryReplayDiscard>>) = register(discardCodecs, typeKey, codec)

    /**
     * 註冊額外事實轉換。
     *
     * @param typeKey 已保存事實種類。
     * @param codec 事實轉換器。
     */
    fun registerFact(typeKey: String, codec: HistoryReplayProjectionCodec<HistoryReplayFact>) = register(factCodecs, typeKey, codec)

    /**
     * 註冊擴充動作的公開投影。
     *
     * @param typeKey 擴充動作 typed envelope 的種類。
     * @param codec 動作轉換器。
     */
    fun registerAction(typeKey: String, codec: HistoryReplayProjectionCodec<HistoryReplayFact>) = register(actionCodecs, typeKey, codec)

    /**
     * 註冊可選規則公開資訊。
     *
     * @param typeKey persistence typed envelope 的種類。
     * @param codec 公開資訊轉換器。
     */
    fun registerOptionalRule(typeKey: String, codec: HistoryReplayProjectionCodec<HistoryReplayRuleInformation>) = register(optionalRuleCodecs, typeKey, codec)

    /**
     * 註冊從玩家規則狀態取出移出手牌的牌（例如三人日麻拔出的北）的轉換。
     *
     * @param typeKey 玩家規則狀態 typed envelope 的種類。
     * @param codec 依移出順序回傳牌參照的轉換器。
     */
    fun registerSetAsideTiles(typeKey: String, codec: HistoryReplayProjectionCodec<List<HistoryTileReference>>) = register(setAsideTileCodecs, typeKey, codec)

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
     */
    private fun <T> register(target: MutableMap<String, HistoryReplayProjectionCodec<T>>, typeKey: String, codec: HistoryReplayProjectionCodec<T>) {
        check(!frozen) { "History replay projection registry is frozen" }
        require(typeKey.isNotBlank()) { "History replay projection type key must not be blank" }
        require(typeKey !in target) { "Duplicate history replay projection type key: $typeKey" }
        target[typeKey] = codec
    }
}
