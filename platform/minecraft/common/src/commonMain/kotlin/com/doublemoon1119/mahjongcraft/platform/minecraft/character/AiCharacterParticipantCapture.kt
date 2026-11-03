package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomParticipantIdentity
import kotlin.uuid.Uuid

/**
 * 已在權威交易外擷取的實體外觀；只有對應房間與來源成功加入時才保存。
 *
 * @property roomInstanceId 預檢時的房間生命週期。
 * @property playerId 本次分配的成員編號。
 * @property identity 經接入端驗證的穩定來源。
 * @property appearance 已脫離原生物件的外觀。
 */
data class AiCharacterParticipantCapture(
    val roomInstanceId: Uuid,
    val playerId: Uuid,
    val identity: RoomParticipantIdentity,
    val appearance: AiCharacterSnapshot,
)
