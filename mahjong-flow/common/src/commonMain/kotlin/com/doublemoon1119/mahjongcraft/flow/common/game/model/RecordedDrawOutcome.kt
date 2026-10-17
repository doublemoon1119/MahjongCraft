package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import kotlin.uuid.Uuid

/**
 * 從新增的流局動作還原出的純結果資料。
 *
 * 這個型別只描述已記錄的流局事實，不決定莊家是否連莊；權威流程應把它交給規則模組的
 * `resolveRoundTransition` 建立正式的回合結束摘要。事件投影只能使用這些資料建立呈現事件，
 * 不得重新計算回合推進決策。
 *
 * @property reason 流局原因。
 * @property classification 流局的規則中立分類。
 * @property beneficiaryPlayerIds 荒牌流局中記錄流局動作的得利玩家；途中流局時為空集合。
 * @property responsiblePlayerIds 此結果的責任玩家；目前流局結果沒有責任歸屬時為空集合。
 */
data class RecordedDrawOutcome(
    val reason: ExhaustiveDrawReason,
    val classification: RoundCompletionClassification,
    val beneficiaryPlayerIds: Set<Uuid>,
    val responsiblePlayerIds: Set<Uuid> = emptySet(),
) {
    /** 本次流局原因的完整 namespaced 結果識別碼。 */
    val outcomeId: String
        get() = reason.id
}
