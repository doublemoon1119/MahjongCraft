package com.doublemoon1119.mahjongcraft.testing.application.common.room.service

import com.doublemoon1119.mahjongcraft.application.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.application.common.room.model.LeaveReason
import com.doublemoon1119.mahjongcraft.application.common.room.service.RoomNotificationService
import java.util.*

/**
 * 供測試使用的 [RoomNotificationService] 模擬實作。
 *
 * 紀錄所有發送出的房間事件通知，以便在單元測試中驗證業務邏輯是否正確向特定玩家發送了關於特定目標的事件。
 */
class FakeRoomNotificationService : RoomNotificationService {
    /** * 紀錄加入通知。
     * Key: Triple(房間ID, 接收者ID, 事件主體ID)
     */
    private val joinNotifications = mutableMapOf<Triple<UUID, UUID, UUID>, JoinReason>()

    /** * 紀錄離開通知。
     * Key: Triple(房間ID, 接收者ID, 事件主體ID)
     */
    private val leaveNotifications = mutableMapOf<Triple<UUID, UUID, UUID>, LeaveReason>()

    /**
     * 紀錄加入事件通知。
     *
     * @param roomId 房間 UUID。
     * @param targetPlayerId 接收通知的房間成員 UUID。
     * @param joinedPlayerId 實際加入房間的玩家 UUID。
     * @param reason 加入的原因。
     */
    override suspend fun notifyJoin(
        roomId: UUID,
        targetPlayerId: UUID,
        joinedPlayerId: UUID,
        reason: JoinReason
    ) {
        joinNotifications[Triple(roomId, targetPlayerId, joinedPlayerId)] = reason
    }

    /**
     * 紀錄離開事件通知。
     *
     * @param roomId 房間 UUID。
     * @param targetPlayerId 接收通知的房間成員 UUID。
     * @param leftPlayerId 實際離開房間的玩家 UUID。
     * @param reason 離開的原因。
     */
    override suspend fun notifyLeave(
        roomId: UUID,
        targetPlayerId: UUID,
        leftPlayerId: UUID,
        reason: LeaveReason
    ) {
        leaveNotifications[Triple(roomId, targetPlayerId, leftPlayerId)] = reason
    }

    /**
     * 獲取特定玩家收到的加入通知原因。
     *
     * @param roomId 房間 UUID。
     * @param targetPlayerId 接收通知的成員 UUID。
     * @param joinedPlayerId 發生事件的目標玩家 UUID。
     * @return 該事件的 [JoinReason]，若無紀錄則回傳 null。
     */
    fun getJoinReason(
        roomId: UUID,
        targetPlayerId: UUID,
        joinedPlayerId: UUID
    ): JoinReason? = joinNotifications[Triple(roomId, targetPlayerId, joinedPlayerId)]

    /**
     * 獲取特定玩家收到的離開通知原因。
     *
     * @param roomId 房間 UUID。
     * @param targetPlayerId 接收通知的成員 UUID。
     * @param leftPlayerId 發生事件的目標玩家 UUID。
     * @return 該事件的 [LeaveReason]，若無紀錄則回傳 null。
     */
    fun getLeaveReason(
        roomId: UUID,
        targetPlayerId: UUID,
        leftPlayerId: UUID
    ): LeaveReason? = leaveNotifications[Triple(roomId, targetPlayerId, leftPlayerId)]
}