package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective

/** 歷史規則專屬公開資訊。
 * @property typeKey 穩定種類識別碼。
 * @property summary 不含私有命令或原始資料的公開摘要；種類未註冊時為 null，表示資訊不可取得。
 */
data class HistoryReplayRuleInformation(val typeKey: String, val summary: String?)

/** 歷史局結算結果。
 * @property reasonId 結算原因識別碼。
 * @property beneficiarySeats 保存的受益玩家座位，不假設結果必為和牌。
 * @property scoresBySeat 依座位索引排列的分數。
 * @property classification 保存的局結算分類；整場完成事實不含此資料。
 * @property responsibleSeats 保存的責任玩家座位；整場完成事實不含此資料。
 * @property transitionDirective 保存的莊家推進決策；整場完成事實不含此資料。
 */
data class HistoryRoundOutcome(
    val reasonId: String,
    val beneficiarySeats: List<Int>,
    val scoresBySeat: Map<Int, Int>,
    val classification: RoundCompletionClassification? = null,
    val responsibleSeats: List<Int> = emptyList(),
    val transitionDirective: RoundTransitionDirective? = null,
)

/** 歷史語意事實。 */
sealed interface HistoryReplayFact {
    /** 事實種類識別碼。 */
    val typeKey: String

    /** 已接受動作。
     * @property typeKey 動作事實種類。
     * @property actorSeat 發起者座位。
     * @property actionType 動作種類。
     * @property directTiles 直接涉及的牌。
     * @property revealedTiles 新公開的牌。
     * @property extensionTypeId 擴充動作種類。
     */
    data class KnownAction(
        override val typeKey: String,
        val actorSeat: Int?,
        val actionType: String,
        val directTiles: List<HistoryTileReference>,
        val revealedTiles: List<HistoryTileReference>,
        val extensionTypeId: String?,
    ) : HistoryReplayFact

    /** 反應事實。
     * @property typeKey 事實種類。
     * @property actionType 動作種類。
     * @property resolvedActorSeat 裁定玩家座位。
     */
    data class Reaction(
        override val typeKey: String,
        val actionType: String?,
        val resolvedActorSeat: Int?,
    ) : HistoryReplayFact

    /** 開局準備事實。
     * @property typeKey 事實種類。
     * @property stepId 步驟識別碼。
     * @property stepIndex 步驟索引。
     * @property nextStepId 下一步識別碼。
     */
    data class Preparation(
        override val typeKey: String,
        val stepId: String,
        val stepIndex: Int,
        val nextStepId: String?,
    ) : HistoryReplayFact

    /** 完成事實。
     * @property typeKey 事實種類。
     * @property outcome 局結算結果。
     */
    data class Completion(override val typeKey: String, val outcome: HistoryRoundOutcome?) : HistoryReplayFact

    /** 已完成的規則效果。
     * @property typeKey 事實種類。
     * @property reasonId 規則效果識別碼。
     * @property outcome 該效果同時完成本局時保存的結算結果。
     */
    data class RuleEffect(
        override val typeKey: String,
        val reasonId: String,
        val outcome: HistoryRoundOutcome?,
    ) : HistoryReplayFact

    /** 未知擴充事實，只保留識別碼與可驗證的公開參照，不保留私有 payload。
     * @property typeKey 穩定種類識別碼。
     * @property actorSeat 已知行為者的初始座位。
     * @property directTiles 已知直接涉及的局內牌參照。
     * @property revealedTiles 已知新公開的局內牌參照。
     */
    data class Opaque(
        override val typeKey: String,
        val actorSeat: Int? = null,
        val directTiles: List<HistoryTileReference> = emptyList(),
        val revealedTiles: List<HistoryTileReference> = emptyList(),
    ) : HistoryReplayFact
}
