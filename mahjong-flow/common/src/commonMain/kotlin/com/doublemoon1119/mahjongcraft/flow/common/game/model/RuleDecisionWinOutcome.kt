package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.uuid.Uuid

/**
 * 規則在選擇流程中直接成立的和牌結果，例如由規則判定、不經一般自摸或榮和宣告的特殊和牌。
 *
 * 規則選擇完成時與分數變動一起寫入權威狀態；結束本局的流程取用後清除。一般自摸與榮和由桌況判斷，不使用本型別。
 *
 * @property outcomeId 本局結果的完整 namespaced 識別碼。
 * @property winnerPlayerIds 本次和牌的玩家。
 * @property responsiblePlayerIds 需為本次和牌付款負責的玩家；沒有特定責任玩家時為空集合。
 * @property winningTileId 本次和牌成立所依據的公開牌張。
 * @property revealsWinnerHands 是否攤開贏家的手牌；和牌與手牌組成無關、不需攤牌時為 false，例如湊齊花牌成立的和牌。
 */
data class RuleDecisionWinOutcome(
    val outcomeId: String,
    val winnerPlayerIds: Set<Uuid>,
    val responsiblePlayerIds: Set<Uuid>,
    val winningTileId: Uuid,
    val revealsWinnerHands: Boolean,
) {
    init {
        NamespacedId.requireValid(outcomeId) { "Rule decision win outcome ID must be namespaced: $outcomeId" }
        require(winnerPlayerIds.isNotEmpty()) { "Rule decision win must have at least one winner" }
        require(winnerPlayerIds.none { it in responsiblePlayerIds }) {
            "Rule decision winners must not be responsible for their own win"
        }
    }
}
