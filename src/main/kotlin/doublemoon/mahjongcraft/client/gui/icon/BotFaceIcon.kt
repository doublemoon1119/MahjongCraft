package doublemoon.mahjongcraft.client.gui.icon

import doublemoon.mahjongcraft.game.mahjong.riichi.model.MahjongTile
import io.github.cottonmc.cotton.gui.client.ScreenDrawing
import io.github.cottonmc.cotton.gui.widget.data.Color
import io.github.cottonmc.cotton.gui.widget.icon.Icon
import net.minecraft.client.gui.DrawContext

class BotFaceIcon(
    val code: Int,
) : Icon {
    override fun paint(context: DrawContext, x: Int, y: Int, size: Int) {
        val width = size * 48 / 64
        val imgX = x + (size - width) / 2
        ScreenDrawing.texturedRect(
            context,
            imgX,
            y,
            width,
            size,
            MahjongTile.entries[code].surfaceIdentifier,
            Color.WHITE.toRgb()
        )
    }
}