package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 本局保存的下一任莊家的持久化資料。 */
@Serializable
sealed interface RoundTransitionDirectivePersistenceDto {
    /** 莊家連莊。 */
    @Serializable
    @SerialName("repeat_dealer")
    data object RepeatDealer : RoundTransitionDirectivePersistenceDto

    /** 交給座位順序中莊家的下一位。 */
    @Serializable
    @SerialName("pass_to_next_seat")
    data object PassToNextSeat : RoundTransitionDirectivePersistenceDto

    /**
     * 交給規則指定的玩家。
     *
     * @property nextDealerPlayerId 下一任莊家的 UUID 字串。
     */
    @Serializable
    @SerialName("pass_to")
    data class PassTo(val nextDealerPlayerId: String) : RoundTransitionDirectivePersistenceDto
}
