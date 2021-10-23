package doublemoon.mahjongcraft.client.gui.icon

import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongTile
import io.github.cottonmc.cotton.gui.client.ScreenDrawing
import io.github.cottonmc.cotton.gui.widget.data.Color
import io.github.cottonmc.cotton.gui.widget.icon.Icon
import net.minecraft.client.util.math.MatrixStack

class BotFaceIcon(
    val code: Int,
) : Icon {
    override fun paint(matrices: MatrixStack?, x: Int, y: Int, size: Int) {
        val width = size * 48 / 64
        val imgX = x + (size - width) / 2
        ScreenDrawing.texturedRect(
            matrices,
            imgX,
            y,
            width,
            size,
            MahjongTile.values()[code].surfaceIdentifier,
            Color.WHITE.toRgb()
        )
    }
}