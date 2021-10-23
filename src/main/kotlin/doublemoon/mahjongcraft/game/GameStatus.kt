package doublemoon.mahjongcraft.game

import doublemoon.mahjongcraft.MOD_ID


/**
 * 遊戲的狀態
 * */
enum class GameStatus {
    WAITING,
    PLAYING;

    val lang: String = "$MOD_ID.game.status.${name.lowercase()}"
}