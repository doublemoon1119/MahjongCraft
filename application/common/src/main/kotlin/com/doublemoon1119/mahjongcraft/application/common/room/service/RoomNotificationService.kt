package com.doublemoon1119.mahjongcraft.application.common.room.service

import com.doublemoon1119.mahjongcraft.application.common.room.model.LeaveReason
import java.util.*

/**
 * 房間事件通知服務。
 *
 * 負責將房間相關的非同步事件通知給特定的觀察者。
 */
interface RoomNotificationService {
    /**
     * 通知觀察者房間已解散或其身分已變更。
     *
     * @param roomId 房間 UUID。
     * @param observerId 接收通知的觀察者 UUID。
     * @param reason 變更的原因。
     */
    suspend fun notifyLeave(roomId: UUID, observerId: UUID, reason: LeaveReason)
}