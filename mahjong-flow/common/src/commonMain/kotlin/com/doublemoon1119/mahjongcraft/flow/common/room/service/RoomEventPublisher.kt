package com.doublemoon1119.mahjongcraft.flow.common.room.service

import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.LeaveReason
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import kotlin.uuid.Uuid

/**
 * 房間事件發布器。
 *
 * 負責處理房間內異步事件的廣播與通知，確保房間成員能接收到其他玩家狀態變更的消息。
 */
interface RoomEventPublisher {
    /**
     * 通知房間成員有玩家加入。
     *
     * @param roomId 房間 Uuid。
     * @param targetPlayerId 接收此通知的房間成員 Uuid。
     * @param joinedPlayerId 實際加入房間的玩家 Uuid。
     * @param reason 加入的原因。
     */
    suspend fun publishJoin(
        roomId: Uuid,
        targetPlayerId: Uuid,
        joinedPlayerId: Uuid,
        reason: JoinReason
    )

    /**
     * 通知房間成員有玩家離開。
     *
     * @param roomId 房間 Uuid。
     * @param targetPlayerId 接收此通知的房間成員 Uuid。
     * @param leftPlayerId 實際離開房間的玩家 Uuid。
     * @param reason 離開的原因。
     */
    suspend fun publishLeave(
        roomId: Uuid,
        targetPlayerId: Uuid,
        leftPlayerId: Uuid,
        reason: LeaveReason
    )

    /**
     * 通知房間成員有玩家切換準備狀態。
     *
     * @param roomId 房間 Uuid。
     * @param targetPlayerId 接收此通知的房間成員 Uuid。
     * @param readyPlayerId 切換準備狀態的玩家 Uuid。
     * @param isReady 該玩家目前的準備狀態。
     */
    suspend fun publishReady(
        roomId: Uuid,
        targetPlayerId: Uuid,
        readyPlayerId: Uuid,
        isReady: Boolean
    )

    /**
     * 通知房間成員房間配置已變更。
     *
     * @param roomId 房間 Uuid。
     * @param targetPlayerId 接收此通知的房間成員 Uuid。
     * @param newConfig 變更後的配置實例。
     */
    suspend fun publishConfigChanged(
        roomId: Uuid,
        targetPlayerId: Uuid,
        newConfig: MahjongRuleConfig
    )
}
