package com.doublemoon1119.mahjongcraft.flow.common.room.model

/**
 * 定義玩家離開房間的語義原因。
 *
 * 用於協助判斷應發送何種通知或執行何種平台動作。
 */
sealed interface LeaveReason {
    /** 玩家主動點擊退出或斷線。 */
    data object Voluntary : LeaveReason

    /** 房主解散房間，導致所有成員強制退出。 */
    data object Dissolved : LeaveReason

    /** 玩家被房主剔除。 */
    data object Kicked : LeaveReason
}