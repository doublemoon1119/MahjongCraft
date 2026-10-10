package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

/** 本局自動操作更新遭拒絕的原因。 */
enum class AutomaticControlUpdateRejection {
    /** 連線玩家不是指定對局的成員。 */
    PLAYER_NOT_IN_GAME,

    /** 提交內容含有不符合 namespaced ID 格式的控制。 */
    INVALID_CONTROL_ID,

    /** 提交內容含有目前規則未宣告支援的控制。 */
    UNSUPPORTED_CONTROL_ID,
}
