package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 胡牌後續決策的明確持久化表示。 */
@Serializable
sealed interface WinRoundDirectivePersistenceDto {
    /** 結束目前這一局。 */
    @Serializable
    @SerialName("end_round")
    data object EndRound : WinRoundDirectivePersistenceDto

    /**
     * 依指定結算模式繼續本局，並交由下一位玩家行動。
     *
     * @property newlyFinishedPlayerIds 因本次結算而完成的玩家 UUID 字串。
     * @property nextPlayerId 下一個行動玩家的 UUID 字串。
     * @property settlementDetail 結算模式的列舉名稱。
     */
    @Serializable
    @SerialName("continue_round")
    data class ContinueRound(val newlyFinishedPlayerIds: Set<String>, val nextPlayerId: String, val settlementDetail: String) : WinRoundDirectivePersistenceDto
}
