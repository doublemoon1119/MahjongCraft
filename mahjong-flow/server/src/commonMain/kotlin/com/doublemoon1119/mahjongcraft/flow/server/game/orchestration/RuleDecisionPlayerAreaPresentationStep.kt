package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.DrawFollowUpPresentationStep
import kotlin.uuid.Uuid

/** 規則選擇步驟完成時，需要依序交給平台呈現的一次玩家牌區變化。 */
sealed interface RuleDecisionPlayerAreaPresentationStep {
    /** 此次牌區變化所屬的玩家。 */
    val playerId: Uuid

    /**
     * 補入一張牌到摸牌位，以一般摸牌呈現；例如先前移出牌時暫停的補牌，在選擇完成後補入。
     *
     * 同一次結果沒有 [Replacement] 時，同一位玩家只能有一次，且必須排在該玩家其他牌區變化之前；有 [Replacement] 時與
     * 其他步驟一起依結果中的順序呈現。
     *
     * @property playerId 補入牌的玩家。
     * @property tileId 補入摸牌位的牌。
     */
    data class Draw(
        override val playerId: Uuid,
        val tileId: Uuid,
    ) : RuleDecisionPlayerAreaPresentationStep

    /**
     * 把手牌中的牌一次移出，再依序補牌；牌原本可以在立牌或摸牌位，例如開局時各家依序補花。
     *
     * 同一次結果有這種步驟時，所有步驟依結果中的順序逐步呈現，跨玩家也一樣，且不能同時有 [FollowUp]。補到的牌最後在
     * 立牌或摸牌位，以結果的桌況為準。
     *
     * @property playerId 移出並補牌的玩家。
     * @property setAsideTileIds 這一步移出的牌，依移出順序排列；只補牌時為空清單。
     * @property drawnTileIds 這一步補到的牌，依補牌順序排列；移出後暫停補牌時為空清單。
     */
    data class Replacement(
        override val playerId: Uuid,
        val setAsideTileIds: List<Uuid>,
        val drawnTileIds: List<Uuid>,
    ) : RuleDecisionPlayerAreaPresentationStep {
        init {
            require(setAsideTileIds.isNotEmpty() || drawnTileIds.isNotEmpty()) { "A replacement step must move at least one tile" }
        }
    }

    /**
     * 從摸牌位移出一張牌並依規則補牌。
     *
     * @property playerId 移出並接收補牌的玩家。
     * @property followUpStep 已成立的移出牌與補牌步驟。
     */
    data class FollowUp(
        override val playerId: Uuid,
        val followUpStep: DrawFollowUpPresentationStep,
    ) : RuleDecisionPlayerAreaPresentationStep
}
