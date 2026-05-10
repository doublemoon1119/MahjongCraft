package com.doublemoon1119.mahjongcraft.application.common.room.service

import com.doublemoon1119.mahjongcraft.application.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.application.common.room.model.LeaveReason
import java.util.UUID

/**
 * 房間事件通知服務。
 *
 * 負責處理房間內異步事件的廣播與通知，確保房間成員能接收到其他玩家狀態變更的消息。
 */
interface RoomNotificationService {
    /**
     * 通知房間成員有玩家加入。
     *
     * @param roomId 房間 UUID。
     * @param targetPlayerId 接收此通知的房間成員 UUID。
     * @param joinedPlayerId 實際加入房間的玩家 UUID。
     * @param reason 加入的原因。
     */
    suspend fun notifyJoin(
        roomId: UUID,
        targetPlayerId: UUID,
        joinedPlayerId: UUID,
        reason: JoinReason
    )

    /**
     * 通知房間成員有玩家離開。
     *
     * @param roomId 房間 UUID。
     * @param targetPlayerId 接收此通知的房間成員 UUID。
     * @param leftPlayerId 實際離開房間的玩家 UUID。
     * @param reason 離開的原因。
     */
    suspend fun notifyLeave(
        roomId: UUID,
        targetPlayerId: UUID,
        leftPlayerId: UUID,
        reason: LeaveReason
    )
}