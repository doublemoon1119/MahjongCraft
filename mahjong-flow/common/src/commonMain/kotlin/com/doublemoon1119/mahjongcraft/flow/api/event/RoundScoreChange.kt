package com.doublemoon1119.mahjongcraft.flow.api.event

import kotlin.jvm.JvmSynthetic
import kotlin.uuid.Uuid

/**
 * 一名玩家在一次結算前後的分數與名次。名次依規則的回合排名決定，從 1 開始。
 *
 * @property playerId 玩家 UUID。
 * @property previousScore 結算前的分數。
 * @property currentScore 結算後的分數。
 * @property previousRank 結算前的名次。
 * @property currentRank 結算後的名次。
 */
class RoundScoreChange private constructor(
    val playerId: Uuid,
    val previousScore: Int,
    val currentScore: Int,
    val previousRank: Int,
    val currentRank: Int,
) {
    /** 事件系統內部建立資料的入口。 */
    internal companion object {
        /**
         * 建立一名玩家結算前後的資料。
         *
         * @param playerId 玩家的 UUID。
         * @param previousScore 結算前的分數。
         * @param currentScore 結算後的分數。
         * @param previousRank 結算前的名次。
         * @param currentRank 結算後的名次。
         * @return 建立的結算分數資料。
         */
        @JvmSynthetic
        fun create(
            playerId: Uuid,
            previousScore: Int,
            currentScore: Int,
            previousRank: Int,
            currentRank: Int,
        ): RoundScoreChange = RoundScoreChange(playerId, previousScore, currentScore, previousRank, currentRank)
    }
}
