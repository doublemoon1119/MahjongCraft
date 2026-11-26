package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.flow.common.room.model.LeaveReason
import kotlinx.serialization.Serializable

/** [LeaveReason] 的網路 DTO。 */
@Serializable
sealed interface LeaveReasonDto {
    /** 玩家主動退出或斷線。 */
    @Serializable data object Voluntary : LeaveReasonDto

    /** 房間解散而離開。 */
    @Serializable data object Dissolved : LeaveReasonDto

    /** 玩家被房主剔除。 */
    @Serializable data object Kicked : LeaveReasonDto
}
