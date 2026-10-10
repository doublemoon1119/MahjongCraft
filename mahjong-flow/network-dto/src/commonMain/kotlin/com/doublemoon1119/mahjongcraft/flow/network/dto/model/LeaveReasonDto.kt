package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.flow.common.room.model.LeaveReason
import kotlinx.serialization.Serializable

/** [LeaveReason] 的網路 DTO。 */
@Serializable
sealed interface LeaveReasonDto {
    @Serializable data object Voluntary : LeaveReasonDto

    @Serializable data object Dissolved : LeaveReasonDto

    @Serializable data object Kicked : LeaveReasonDto
}
