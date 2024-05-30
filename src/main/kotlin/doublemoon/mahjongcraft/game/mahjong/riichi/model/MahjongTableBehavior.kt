package doublemoon.mahjongcraft.game.mahjong.riichi.model

import doublemoon.mahjongcraft.network.MahjongTablePacketListener

/**
 * 麻將桌行為, 發送數據包 [MahjongTablePacketListener] 用
 * */
enum class MahjongTableBehavior {
    //c2s
    JOIN,
    LEAVE,
    READY,
    NOT_READY,
    START,
    KICK,
    ADD_BOT,
    CHANGE_RULE,

    //s2c
    OPEN_TABLE_WAITING_GUI,
    OPEN_TABLE_PLAYING_GUI,

    //both
    OPEN_RULES_EDITOR_GUI,
}
