package com.doublemoon1119.mahjongcraft.flow.network.dto.model
import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.LeaveReason

/**
 * 將領域資料轉換為網路 DTO。
 * @return 轉換後的資料。
 */
fun JoinReason.toDto(): JoinReasonDto = when (this) {
    JoinReason.Created -> JoinReasonDto.Created
    JoinReason.Joined -> JoinReasonDto.Joined
    JoinReason.MatchEnded -> JoinReasonDto.MatchEnded
}

/**
 * 將網路 DTO 還原為領域資料。
 * @return 轉換後的資料。
 */
fun JoinReasonDto.toDomain(): JoinReason = when (this) {
    JoinReasonDto.Created -> JoinReason.Created
    JoinReasonDto.Joined -> JoinReason.Joined
    JoinReasonDto.MatchEnded -> JoinReason.MatchEnded
}

/**
 * 將領域資料轉換為網路 DTO。
 * @return 轉換後的資料。
 */
fun LeaveReason.toDto(): LeaveReasonDto = when (this) {
    LeaveReason.Voluntary -> LeaveReasonDto.Voluntary
    LeaveReason.Dissolved -> LeaveReasonDto.Dissolved
    LeaveReason.Kicked -> LeaveReasonDto.Kicked
}

/**
 * 將網路 DTO 還原為領域資料。
 * @return 轉換後的資料。
 */
fun LeaveReasonDto.toDomain(): LeaveReason = when (this) {
    LeaveReasonDto.Voluntary -> LeaveReason.Voluntary
    LeaveReasonDto.Dissolved -> LeaveReason.Dissolved
    LeaveReasonDto.Kicked -> LeaveReason.Kicked
}
