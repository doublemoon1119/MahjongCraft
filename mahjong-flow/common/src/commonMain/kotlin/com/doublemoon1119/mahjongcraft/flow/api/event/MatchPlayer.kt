package com.doublemoon1119.mahjongcraft.flow.api.event

import kotlin.jvm.JvmSynthetic
import kotlin.uuid.Uuid

/**
 * 對局中的一名玩家。
 *
 * @property playerId 玩家 UUID。
 * @property isAi 是否由 AI 操控。
 * @property initialSeatIndex 起家座位，從 0 開始。
 */
class MatchPlayer private constructor(
    val playerId: Uuid,
    val isAi: Boolean,
    val initialSeatIndex: Int,
) {
    /** 事件系統內部建立資料的入口。 */
    internal companion object {
        /**
         * 建立一名玩家的資料。
         *
         * @param playerId 玩家的 UUID。
         * @param isAi 是否由 AI 操控。
         * @param initialSeatIndex 起家座位，從 0 開始。
         * @return 建立的玩家資料。
         */
        @JvmSynthetic
        fun create(
            playerId: Uuid,
            isAi: Boolean,
            initialSeatIndex: Int,
        ): MatchPlayer = MatchPlayer(playerId, isAi, initialSeatIndex)
    }
}
