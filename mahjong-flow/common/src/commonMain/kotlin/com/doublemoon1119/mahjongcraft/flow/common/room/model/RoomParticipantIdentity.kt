package com.doublemoon1119.mahjongcraft.flow.common.room.model

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.uuid.Uuid

/**
 * 參與者的穩定來源身分，不隨本次分配的成員編號改變。
 *
 * @property sourceId 可信任來源的完整識別碼；由接入端決定，不接受參與者自行聲稱。
 * @property subjectId 來源內穩定且唯一的身分。
 */
data class RoomParticipantIdentity(
    val sourceId: String,
    val subjectId: Uuid,
) {
    init {
        NamespacedId.requireValid(sourceId) { "Invalid participant source ID" }
    }

    /** 真人身分的共用來源。 */
    companion object {
        /** 真人來源識別碼。 */
        const val PLAYER_SOURCE: String = "mahjongcraft:player"

        /**
         * 建立真人身分。
         *
         * @param playerId 玩家的穩定編號。
         * @return 不依賴連線狀態的身分。
         */
        fun player(playerId: Uuid): RoomParticipantIdentity = RoomParticipantIdentity(PLAYER_SOURCE, playerId)
    }
}
