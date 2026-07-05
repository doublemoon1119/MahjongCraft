package com.doublemoon1119.mahjongcraft.flow.common.room.model

/**
 * 定義玩家加入房間的原因。
 */
sealed interface JoinReason {
    /** 房主創建房間。 */
    data object Created : JoinReason

    /** 玩家主動加入房間。 */
    data object Joined : JoinReason
}