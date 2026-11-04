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
     * 同一位玩家在同一次結果中只能有一次，且必須排在該玩家其他牌區變化之前。
     *
     * @property playerId 補入牌的玩家。
     * @property tileId 補入摸牌位的牌。
     */
    data class Draw(
        override val playerId: Uuid,
        val tileId: Uuid,
    ) : RuleDecisionPlayerAreaPresentationStep

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
