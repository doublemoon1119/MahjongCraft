package com.doublemoon1119.mahjongcraft.application.common.room.model

/**
 * 定義玩家加入房間的原因。
 */
enum class JoinReason {
    /** 房主創建房間。 */
    Created,

    /** 玩家主動加入房間。 */
    Joined
}