package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState
import kotlin.uuid.Uuid

/** 開局只能採用同一房間全部外部成員已確認的入座與資格，不沿用先前掃描結果。 */
object SeatedParticipantStartPreflight {
    /**
     * 將完整預檢轉成同一開局交易使用的外觀。
     *
     * @param room 即將開局的房間。
     * @param observations 本次預檢取得的狀態，鍵為邏輯成員編號。
     * @return 全部符合時的擷取結果；缺少、未知、不合格或來源不同時為 null。
     */
    fun capture(room: Room, observations: Map<Uuid, SeatedParticipantObservation>): List<AiCharacterParticipantCapture>? = room.admission.memberIdentities.map { (player, identity) ->
        val observation = observations[player] ?: return null
        if (observation.identity != identity || observation.seatState != SeatState.SEATED) return null
        val appearance = observation.appearance ?: return null
        AiCharacterParticipantCapture(room.admission.instanceId, player, identity, appearance)
    }
}
