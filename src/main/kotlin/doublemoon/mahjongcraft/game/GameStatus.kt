package doublemoon.mahjongcraft.game

import doublemoon.mahjongcraft.MOD_ID
import doublemoon.mahjongcraft.util.TextFormatting
import net.minecraft.text.TranslatableText


/**
 * 遊戲的狀態
 * */
enum class GameStatus : TextFormatting {
    WAITING,
    PLAYING;

    override fun toText() = TranslatableText("$MOD_ID.game.status.${name.lowercase()}")
}