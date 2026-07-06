package com.doublemoon1119.mahjongcraft.flow.common.room.model

import com.doublemoon1119.mahjongcraft.flow.common.error.ApplicationError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import kotlin.uuid.Uuid

/**
 * 與房間操作相關的應用層錯誤定義。
 *
 * 涵蓋所有房間相關 use case 中可能出現的業務邏輯錯誤情境，供 [Outcome] 的錯誤型別使用。
 *
 * 每種錯誤皆包含足夠的上下文資訊（如 roomId、playerId），
 * 方便呼叫端進行細緻的錯誤處理或使用者提示。
 */
sealed interface RoomError : ApplicationError {

    /**
     * 找不到指定的房間。
     *
     * @param roomId 欲操作的房間 Uuid。
     */
    data class RoomNotFound(val roomId: Uuid) : RoomError

    /**
     * 房間已經存在，無法重複創建。
     *
     * @param roomId 已存在的房間 Uuid。
     */
    data class RoomAlreadyExists(val roomId: Uuid) : RoomError

    /**
     * 目標玩家不在指定的房間內。
     *
     * @param playerId 目標玩家 Uuid。
     * @param roomId 房間 Uuid。
     */
    data class PlayerNotInRoom(val playerId: Uuid, val roomId: Uuid) : RoomError

    /**
     * 玩家已在房間內，無法重複加入。
     *
     * @param playerId 玩家 Uuid。
     * @param roomId 房間 Uuid。
     */
    data class PlayerAlreadyInRoom(val playerId: Uuid, val roomId: Uuid) : RoomError

    /**
     * 房間人數已滿，無法再加入新成員。
     *
     * @param roomId 房間 Uuid。
     */
    data class RoomIsFull(val roomId: Uuid) : RoomError

    /**
     * 操作者不具備房主權限。
     *
     * @param playerId 發起操作的玩家 Uuid。
     */
    data class NotHost(val playerId: Uuid) : RoomError

    /**
     * 房主不能對自己執行踢出操作。
     *
     * @param playerId 房主玩家的 Uuid。
     */
    data class HostCannotKickSelf(val playerId: Uuid) : RoomError
}
