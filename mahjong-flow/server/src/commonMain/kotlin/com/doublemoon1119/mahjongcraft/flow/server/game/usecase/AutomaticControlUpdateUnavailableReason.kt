package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

/** 無法提供或更新本局自動操作狀態的原因。 */
enum class AutomaticControlUpdateUnavailableReason {
    /** 指定對局不存在。 */
    GAME_NOT_FOUND,

    /** 指定對局已經結束。 */
    MATCH_OVER,
}
