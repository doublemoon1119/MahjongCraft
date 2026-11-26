package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.uuid.Uuid

/** 玩家對規則選擇步驟提交的受控資料。 */
sealed interface RuleDecisionSubmission {
    /** 已確認。 */
    data object Confirmed : RuleDecisionSubmission

    /**
     * 已選擇指定選項。
     *
     * @property optionId 選擇的完整命名空間識別碼。
     */
    data class Choice(val optionId: String) : RuleDecisionSubmission

    /**
     * 已選擇指定牌張。
     *
     * @property tileIds 選擇的牌張識別碼集合。
     */
    data class Tiles(val tileIds: Set<Uuid>) : RuleDecisionSubmission
}
