package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayDiscard
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryReplayRuleInformation
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryTileReference
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.BuiltInDiscardPilePersistenceKeys
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.BuiltinHistoryReplayDiscardCodecs
import kotlinx.serialization.json.JsonElement

/** 歷史公開資料的窄責任解碼器。
 * @param T 轉換後的歷史讀模型型別。
 */
fun interface HistoryReplayProjectionCodec<T> {
    /** 解碼公開資料，不回傳原始私有 JSON。
     * @param value 解碼邊界內部的投影。
     * @param context 經驗證的索引及工作預算。
     * @return 歷史讀模型。
     */
    fun decode(value: JsonElement, context: HistoryReplayProjectionContext): T
}

/** 擴充解碼器的受限上下文；不能存取或修改權威遊戲。
 * @property budget 本次讀取的共用工作預算。
 * @property tileCount 截至目前位置已宣告的實體牌數。
 * @property seatCount header 玩家數。
 */
class HistoryReplayProjectionContext internal constructor(
    private val budget: ReplayReadBudget,
    private val tileCount: Int,
    private val seatCount: Int,
) {
    /** 建立同局實體牌參照。
     * @param index 局內實體牌索引，不是牌種字典索引。
     * @return 已驗證參照。
     */
    fun tile(index: Int): HistoryTileReference = HistoryTileReference(budget.tile(index, tileCount))

    /** 驗證 header 初始座位索引。
     * @param index 初始座位。
     * @return 原座位。
     */
    fun seat(index: Int): Int = budget.seat(index, seatCount)

    /** 在配置集合或走訪 payload 前扣除工作預算。
     * @param units 工作單位數。
     */
    fun charge(units: Long = 1L) = budget.charge(units)
}

/** 歷史投影註冊表目前各分類的穩定登記 key。
 *
 * @property discard 必要牌河轉換器的 key。
 * @property fact 額外語意事實轉換器的 key。
 * @property action 擴充動作轉換器的 key。
 * @property optionalRule 可選規則公開資訊轉換器的 key。
 */
data class HistoryReplayProjectionRegistrationKeys(
    val discard: Set<String>,
    val fact: Set<String>,
    val action: Set<String>,
    val optionalRule: Set<String>,
)

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

    /** 註冊是否已禁止變更。 */
    private var frozen = false

    /** 取得各分類目前的穩定登記 key，供 extension bootstrap 診斷使用。 */
    val registrationKeys: HistoryReplayProjectionRegistrationKeys
        get() = HistoryReplayProjectionRegistrationKeys(
            discard = discardCodecs.keys.toSet(),
            fact = factCodecs.keys.toSet(),
            action = actionCodecs.keys.toSet(),
            optionalRule = optionalRuleCodecs.keys.toSet(),
        )

    /** 註冊必要牌河轉換。
     * @param typeKey persistence typed envelope 的種類。
     * @param codec 牌河轉換器。
     */
    fun registerDiscard(typeKey: String, codec: HistoryReplayProjectionCodec<List<HistoryReplayDiscard>>) = register(discardCodecs, typeKey, codec)

    /** 註冊額外事實轉換。
     * @param typeKey 已保存事實種類。
     * @param codec 事實轉換器。
     */
    fun registerFact(typeKey: String, codec: HistoryReplayProjectionCodec<HistoryReplayFact>) = register(factCodecs, typeKey, codec)

    /** 註冊擴充動作的公開投影。
     * @param typeKey 擴充動作 typed envelope 的種類。
     * @param codec 動作轉換器。
     */
    fun registerAction(typeKey: String, codec: HistoryReplayProjectionCodec<HistoryReplayFact>) = register(actionCodecs, typeKey, codec)

    /** 註冊可選規則公開資訊。
     * @param typeKey persistence typed envelope 的種類。
     * @param codec 公開資訊轉換器。
     */
    fun registerOptionalRule(typeKey: String, codec: HistoryReplayProjectionCodec<HistoryReplayRuleInformation>) = register(optionalRuleCodecs, typeKey, codec)

    /** 禁止後續註冊；不改變已註冊順序或內容。 */
    fun freeze() {
        frozen = true
    }

    /** 解碼額外事實。
     * @param typeKey 已保存種類。
     * @param value 內部事實。
     * @param context 受限上下文。
     * @return 已註冊結果；缺少轉換器為 null。
     */
    internal fun decodeFact(typeKey: String, value: JsonElement, context: HistoryReplayProjectionContext): HistoryReplayFact? = factCodecs[typeKey]?.decode(value, context)

    /** 解碼擴充動作。
     * @param typeKey 擴充動作種類。
     * @param value 私有 payload 的邊界內部資料。
     * @param context 受限上下文。
     * @return 公開讀模型；缺少轉換器為 null。
     */
    internal fun decodeAction(typeKey: String, value: JsonElement, context: HistoryReplayProjectionContext): HistoryReplayFact? = actionCodecs[typeKey]?.decode(value, context)

    /** 解碼必要牌河。
     * @param typeKey 牌河種類。
     * @param value 內部 payload。
     * @param context 受限上下文。
     * @return 牌河結果；缺少轉換器為 null。
     */
    internal fun decodeDiscard(typeKey: String, value: JsonElement, context: HistoryReplayProjectionContext): List<HistoryReplayDiscard>? = discardCodecs[typeKey]?.decode(value, context)

    /** 解碼可選規則資料。
     * @param typeKey 規則資料種類。
     * @param value 內部 payload。
     * @param context 受限上下文。
     * @return 公開資訊；缺少轉換器為 null。
     */
    internal fun decodeOptionalRule(typeKey: String, value: JsonElement, context: HistoryReplayProjectionContext): HistoryReplayRuleInformation? = optionalRuleCodecs[typeKey]?.decode(value, context)

    /** 驗證並加入單一分類，不因失敗取代既有項目。
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

/** 明確加入內建規則轉換器，不自動凍結或建立隱性 fallback。
 * @param registry 呼叫端提供的可註冊分類。
 */
fun registerBuiltInHistoryReplayProjections(registry: HistoryReplayProjectionRegistry) {
    registry.registerDiscard(BuiltInDiscardPilePersistenceKeys.RIICHI, BuiltinHistoryReplayDiscardCodecs.riichi)
    registry.registerDiscard(BuiltInDiscardPilePersistenceKeys.TAIWAN, BuiltinHistoryReplayDiscardCodecs.taiwan)
}
