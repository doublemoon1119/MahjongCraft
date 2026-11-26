package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import kotlin.uuid.Uuid

/** 一局結束的方式。 */
sealed interface RoundConclusion {
    /**
     * 有玩家胡牌。
     *
     * @property winnerPlayerIds 這次胡牌的玩家，至少一位。
     */
    data class Win(val winnerPlayerIds: Set<Uuid>) : RoundConclusion {
        init {
            require(winnerPlayerIds.isNotEmpty()) { "A win needs at least one winner" }
        }
    }

    /**
     * 牌摸完而流局。
     *
     * @property reason 流局原因。
     * @property tenpaiPlayerIds 流局時聽牌的玩家。
     */
    data class ExhaustiveDraw(
        val reason: ExhaustiveDrawReason,
        val tenpaiPlayerIds: Set<Uuid>,
    ) : RoundConclusion

    /**
     * 牌還沒摸完就依規則流局，例如途中流局。
     *
     * @property reason 流局原因。
     */
    data class AbortiveDraw(val reason: ExhaustiveDrawReason) : RoundConclusion
}
