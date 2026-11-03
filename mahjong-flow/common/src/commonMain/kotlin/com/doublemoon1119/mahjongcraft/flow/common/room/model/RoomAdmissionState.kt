package com.doublemoon1119.mahjongcraft.flow.common.room.model

import kotlin.uuid.Uuid

/**
 * 同一房間生命週期內的成員來源與暫時排除資料。
 *
 * 開局與回房仍保留來源及排除資料；只有解散才結束這份資料的生命週期。
 *
 * @property instanceId 這次房間的識別碼；同一場地重新建房會使用另一個值。
 * @property memberIdentities 非預設來源的成員身分；真人未列入時使用玩家身分。
 * @property excludedParticipants 已排除的來源身分與上一次成員編號，供房主辨識及解除。
 */
data class RoomAdmissionState(
    val instanceId: Uuid = Uuid.random(),
    val memberIdentities: Map<Uuid, RoomParticipantIdentity> = emptyMap(),
    val excludedParticipants: Map<RoomParticipantIdentity, Uuid> = emptyMap(),
) {
    init {
        require(excludedParticipants.size <= MAX_EXCLUDED_PARTICIPANTS) { "Too many excluded participants" }
    }

    /** 房間生命週期內的有界管理資料。 */
    companion object {
        /** 限制保存、同步與管理操作的工作量，不截斷已排除的身分。 */
        const val MAX_EXCLUDED_PARTICIPANTS: Int = 64
    }

    /**
     * 取得成員的穩定身分。
     *
     * @param playerId 房間中的成員編號。
     * @return 已記錄來源或預設真人身分。
     */
    fun identityFor(playerId: Uuid): RoomParticipantIdentity = memberIdentities[playerId] ?: RoomParticipantIdentity.player(playerId)

    /**
     * 排除成員並釋放其目前的來源關聯。
     *
     * @param playerId 被移除的成員編號。
     * @return 保留排除記錄的新資料。
     */
    fun exclude(playerId: Uuid): RoomAdmissionState = copy(
        memberIdentities = memberIdentities - playerId,
        excludedParticipants = excludedParticipants + (identityFor(playerId) to playerId),
    )
}
