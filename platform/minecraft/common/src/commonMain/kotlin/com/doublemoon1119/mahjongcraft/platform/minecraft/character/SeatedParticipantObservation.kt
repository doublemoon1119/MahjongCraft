package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomParticipantIdentity
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState

/**
 * 接入端在開局前取得的一位成員狀態，不持有原生物件。
 *
 * @property identity 實際驗證的來源。
 * @property seatState 已複核乘坐關係及位置的席位狀態。
 * @property appearance 當下已確認資格的外觀；無法確認時為 null。
 */
data class SeatedParticipantObservation(
    val identity: RoomParticipantIdentity,
    val seatState: SeatState,
    val appearance: AiCharacterSnapshot?,
)
