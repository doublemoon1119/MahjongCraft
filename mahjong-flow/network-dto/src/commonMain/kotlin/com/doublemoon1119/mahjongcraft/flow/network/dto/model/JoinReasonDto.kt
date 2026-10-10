package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import kotlinx.serialization.Serializable

/** [JoinReason] 的網路 DTO。 */
@Serializable
sealed interface JoinReasonDto {
    @Serializable data object Created : JoinReasonDto

    @Serializable data object Joined : JoinReasonDto

    @Serializable data object MatchEnded : JoinReasonDto
}
