package com.doublemoon1119.mahjongcraft.flow.network.dto

import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.LeaveReason
import kotlinx.serialization.Serializable

@Serializable
sealed interface JoinReasonDto {
    @Serializable data object Created : JoinReasonDto

    @Serializable data object Joined : JoinReasonDto
}

fun JoinReason.toDto(): JoinReasonDto = when (this) {
    JoinReason.Created -> JoinReasonDto.Created
    JoinReason.Joined -> JoinReasonDto.Joined
}

fun JoinReasonDto.toDomain(): JoinReason = when (this) {
    JoinReasonDto.Created -> JoinReason.Created
    JoinReasonDto.Joined -> JoinReason.Joined
}

@Serializable
sealed interface LeaveReasonDto {
    @Serializable data object Voluntary : LeaveReasonDto

    @Serializable data object Dissolved : LeaveReasonDto

    @Serializable data object Kicked : LeaveReasonDto
}

fun LeaveReason.toDto(): LeaveReasonDto = when (this) {
    LeaveReason.Voluntary -> LeaveReasonDto.Voluntary
    LeaveReason.Dissolved -> LeaveReasonDto.Dissolved
    LeaveReason.Kicked -> LeaveReasonDto.Kicked
}

fun LeaveReasonDto.toDomain(): LeaveReason = when (this) {
    LeaveReasonDto.Voluntary -> LeaveReason.Voluntary
    LeaveReasonDto.Dissolved -> LeaveReason.Dissolved
    LeaveReasonDto.Kicked -> LeaveReason.Kicked
}
