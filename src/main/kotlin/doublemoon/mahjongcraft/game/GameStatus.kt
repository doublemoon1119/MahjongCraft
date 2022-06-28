package doublemoon.mahjongcraft.game

import doublemoon.mahjongcraft.MOD_ID
import doublemoon.mahjongcraft.util.TextFormatting
import net.minecraft.text.Text


/**
 * 遊戲的狀態
 * */
enum class GameStatus : TextFormatting {
    WAITING,
    PLAYING;

    override fun toText(): Text = Text.translatable("$MOD_ID.game.status.${name.lowercase()}")
}