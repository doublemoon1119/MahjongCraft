package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import kotlinx.serialization.Serializable

/** [JoinReason] 的網路 DTO。 */
@Serializable
sealed interface JoinReasonDto {
    /** 建立房間時加入。 */
    @Serializable data object Created : JoinReasonDto

    /** 加入既有房間。 */
    @Serializable data object Joined : JoinReasonDto

    /** 因對局結束而回到房間。 */
    @Serializable data object MatchEnded : JoinReasonDto
}
