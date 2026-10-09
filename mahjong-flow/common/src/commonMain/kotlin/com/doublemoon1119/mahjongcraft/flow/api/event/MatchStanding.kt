package com.doublemoon1119.mahjongcraft.flow.api.event

import kotlin.jvm.JvmSynthetic
import kotlin.uuid.Uuid

/**
 * 一名玩家在對局結束時的分數與名次。名次依規則的終局排名決定（包含同分時的判定），從 1 開始。
 *
 * @property playerId 玩家 UUID。
 * @property isAi 是否由 AI 操控。
 * @property score 最終分數。
 * @property rank 最終名次。
 */
class MatchStanding private constructor(
    val playerId: Uuid,
    val isAi: Boolean,
    val score: Int,
    val rank: Int,
) {
    /** 事件系統內部建立資料的入口。 */
    internal companion object {
        /**
         * 建立一名玩家的最終分數與名次。
         *
         * @param playerId 玩家的 UUID。
         * @param isAi 是否由 AI 操控。
         * @param score 最終分數。
         * @param rank 最終名次。
         * @return 建立的最終排名資料。
         */
        @JvmSynthetic
        fun create(
            playerId: Uuid,
            isAi: Boolean,
            score: Int,
            rank: Int,
        ): MatchStanding = MatchStanding(playerId, isAi, score, rank)
    }
}
