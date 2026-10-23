package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.uuid.Uuid

/** 整場對局在本局結束後的權威決策。 */
sealed interface MatchProgressionDecision {
    /**
     * 立即結束整場對局。
     *
     * @property reasonId 完整 namespaced 的終局原因 ID。
     */
    data class EndMatch(val reasonId: String) : MatchProgressionDecision {
        init {
            NamespacedId.requireValid(reasonId) { "Match end reason must be a full namespaced id: $reasonId" }
        }
    }

    /**
     * 繼續整場對局。
     *
     * @property transition 下一局位的連莊或過莊決策。
     * @property seatOrder 局間換位後下一局完整的座位順序，也就是出牌順序；必須剛好包含本桌每位玩家各一次。
     * null 表示不換位。
     */
    data class ContinueMatch(
        val transition: MatchRoundTransition,
        val seatOrder: List<Uuid>? = null,
    ) : MatchProgressionDecision
}
