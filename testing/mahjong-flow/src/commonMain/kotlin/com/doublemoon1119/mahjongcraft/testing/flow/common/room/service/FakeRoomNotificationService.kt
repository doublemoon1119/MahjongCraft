package com.doublemoon1119.mahjongcraft.testing.flow.common.room.service

import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.LeaveReason
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomNotificationService
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import kotlin.uuid.Uuid

/**
 * 供測試使用的 [RoomNotificationService] 模擬實作。
 *
 * 紀錄所有發送出的房間事件通知，以便在單元測試中驗證業務邏輯是否正確向特定玩家發送了關於特定目標的事件。
 */
class FakeRoomNotificationService : RoomNotificationService {
    /**
     * 紀錄加入通知。
     *
     * Key: Triple(房間ID, 接收者ID, 事件主體ID)
     */
    private val joinNotifications = mutableMapOf<Triple<Uuid, Uuid, Uuid>, JoinReason>()

    /**
     * 紀錄離開通知。
     *
     * Key: Triple(房間ID, 接收者ID, 事件主體ID)
     */
    private val leaveNotifications = mutableMapOf<Triple<Uuid, Uuid, Uuid>, LeaveReason>()

    /**
     * 紀錄準備狀態通知。
     *
     * Key: Triple(房間ID, 接收者ID, 事件主體ID)
     */
    private val readyNotifications = mutableMapOf<Triple<Uuid, Uuid, Uuid>, Boolean>()

    /**
     * 紀錄房間配置變更通知。
     *
     * Key: Pair(房間ID, 接收者ID)
     */
    private val configChangeNotifications = mutableMapOf<Pair<Uuid, Uuid>, MahjongRuleConfig>()

    /**
     * 紀錄加入事件通知。
     *
     * @param roomId 房間 Uuid。
     * @param targetPlayerId 接收通知的房間成員 Uuid。
     * @param joinedPlayerId 實際加入房間的玩家 Uuid。
     * @param reason 加入的原因。
     */
    override suspend fun notifyJoin(
        roomId: Uuid,
        targetPlayerId: Uuid,
        joinedPlayerId: Uuid,
        reason: JoinReason
    ) {
        joinNotifications[Triple(roomId, targetPlayerId, joinedPlayerId)] = reason
    }

    /**
     * 紀錄離開事件通知。
     *
     * @param roomId 房間 Uuid。
     * @param targetPlayerId 接收通知的房間成員 Uuid。
     * @param leftPlayerId 實際離開房間的玩家 Uuid。
     * @param reason 離開的原因。
     */
    override suspend fun notifyLeave(
        roomId: Uuid,
        targetPlayerId: Uuid,
        leftPlayerId: Uuid,
        reason: LeaveReason
    ) {
        leaveNotifications[Triple(roomId, targetPlayerId, leftPlayerId)] = reason
    }

    /**
     * 紀錄準備狀態變更通知。
     *
     * @param roomId 房間 Uuid。
     * @param targetPlayerId 接收通知的房間成員 Uuid。
     * @param readyPlayerId 切換準備狀態的玩家 Uuid。
     * @param isReady 該玩家目前的準備狀態。
     */
    override suspend fun notifyReady(
        roomId: Uuid,
        targetPlayerId: Uuid,
        readyPlayerId: Uuid,
        isReady: Boolean
    ) {
        readyNotifications[Triple(roomId, targetPlayerId, readyPlayerId)] = isReady
    }

    /**
     * 紀錄房間配置變更通知。
     *
     * @param roomId 房間 Uuid。
     * @param targetPlayerId 接收通知的房間成員 Uuid。
     * @param newConfig 變更後的配置實例。
     */
    override suspend fun notifyConfigChanged(
        roomId: Uuid,
        targetPlayerId: Uuid,
        newConfig: MahjongRuleConfig
    ) {
        configChangeNotifications[roomId to targetPlayerId] = newConfig
    }

    /**
     * 獲取特定玩家收到的加入通知原因。
     *
     * @param roomId 房間 Uuid。
     * @param targetPlayerId 接收通知的成員 Uuid。
     * @param joinedPlayerId 發生事件的目標玩家 Uuid。
     * @return 該事件的 [JoinReason]，若無紀錄則回傳 null。
     */
    fun getJoinReason(
        roomId: Uuid,
        targetPlayerId: Uuid,
        joinedPlayerId: Uuid
    ): JoinReason? = joinNotifications[Triple(roomId, targetPlayerId, joinedPlayerId)]

    /**
     * 獲取特定玩家收到的離開通知原因。
     *
     * @param roomId 房間 Uuid。
     * @param targetPlayerId 接收通知的成員 Uuid。
     * @param leftPlayerId 發生事件的目標玩家 Uuid。
     * @return 該事件的 [LeaveReason]，若無紀錄則回傳 null。
     */
    fun getLeaveReason(
        roomId: Uuid,
        targetPlayerId: Uuid,
        leftPlayerId: Uuid
    ): LeaveReason? = leaveNotifications[Triple(roomId, targetPlayerId, leftPlayerId)]

    /**
     * 獲取特定玩家收到的準備狀態通知。
     *
     * @param roomId 房間 Uuid。
     * @param targetPlayerId 接收通知的成員 Uuid。
     * @param readyPlayerId 發生事件的目標玩家 Uuid。
     * @return 該觀察者收到的準備狀態，若無紀錄則回傳 null。
     */
    fun getReadyStatus(
        roomId: Uuid,
        targetPlayerId: Uuid,
        readyPlayerId: Uuid
    ): Boolean? = readyNotifications[Triple(roomId, targetPlayerId, readyPlayerId)]

    /**
     * 獲取特定玩家收到的配置變更通知。
     *
     * @param roomId 房間 Uuid。
     * @param targetPlayerId 接收通知的成員 Uuid。
     * @return 該觀察者收到的最新配置，若無紀錄則回傳 null。
     */
    fun getConfigChangedNotification(
        roomId: Uuid,
        targetPlayerId: Uuid
    ): MahjongRuleConfig? = configChangeNotifications[roomId to targetPlayerId]
}