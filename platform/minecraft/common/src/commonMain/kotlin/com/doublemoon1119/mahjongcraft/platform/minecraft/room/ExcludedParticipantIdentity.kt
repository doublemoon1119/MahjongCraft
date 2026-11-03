package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomParticipantIdentity
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.SeatedParticipantSources
import kotlin.uuid.Uuid

/**
 * 排除卡片顯示的 UUID，依來源選用實際主體或原成員編號。
 *
 * @property id 顯示與搜尋使用的完整 UUID。
 */
data class ExcludedParticipantIdentity(val id: Uuid) {
    /**
     * 依名稱或完整 UUID 搜尋同名成員，不發出額外查詢。
     *
     * @param name 卡片上顯示的名稱。
     * @param query 搜尋文字。
     * @return 名稱或 UUID 含有搜尋文字時為 true。
     */
    fun matches(name: String, query: String): Boolean = name.contains(query, ignoreCase = true) || id.toString().contains(query, ignoreCase = true)

    /** 不把未知來源的主體編號猜成實體 UUID。 */
    companion object {
        /**
         * 從房主快照取得可辨識的來源，其他觀察者沒有顯示權限。
         *
         * @param room 房間的觀察者快照。
         * @param member 被排除者的原成員編號。
         * @return 可顯示的身分；沒有權限或不在名單時為 null。
         */
        fun resolve(room: RoomSnapshot, member: Uuid): ExcludedParticipantIdentity? {
            if (!room.isHost || member !in room.excludedPlayerIds) return null
            val identity = room.excludedParticipantIdentities[member]
            return when (identity?.sourceId) {
                RoomParticipantIdentity.PLAYER_SOURCE -> ExcludedParticipantIdentity(identity.subjectId)
                SeatedParticipantSources.WORLD_ENTITY -> ExcludedParticipantIdentity(identity.subjectId)
                else -> ExcludedParticipantIdentity(member)
            }
        }
    }
}
