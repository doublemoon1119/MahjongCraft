package com.doublemoon1119.mahjongcraft.testing.application.common.room.service

import com.doublemoon1119.mahjongcraft.application.common.room.model.LeaveReason
import com.doublemoon1119.mahjongcraft.application.common.room.service.RoomNotificationService
import java.util.*

/**
 * 供測試使用的 [RoomNotificationService] 簡易實作。
 *
 * 紀錄所有發送出的離開通知，以便在單元測試中驗證業務邏輯是否正確觸發了通知。
 */
class FakeRoomNotificationService : RoomNotificationService {
    /** 紀錄發送給特定觀察者的最後一個離開原因。 */
    private val notifications = mutableMapOf<Pair<UUID, UUID>, LeaveReason>()

    /**
     * 紀錄離開通知。
     *
     * @param roomId 房間 UUID。
     * @param observerId 觀察者 UUID。
     * @param reason 離開原因。
     */
    override suspend fun notifyLeave(roomId: UUID, observerId: UUID, reason: LeaveReason) {
        notifications[roomId to observerId] = reason
    }

    /**
     * 獲取指定觀察者收到的離開原因。
     *
     * @param roomId 房間 UUID。
     * @param observerId 觀察者 UUID。
     * @return 該觀察者收到的 [LeaveReason]，若無紀錄則回傳 null。
     */
    fun getReceivedReason(roomId: UUID, observerId: UUID): LeaveReason? {
        return notifications[roomId to observerId]
    }

    /**
     * 清除所有通知紀錄。
     */
    fun clear() {
        notifications.clear()
    }
}