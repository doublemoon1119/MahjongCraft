package doublemoon.mahjongcraft.client.gui.widget

import doublemoon.mahjongcraft.game.mahjong.riichi.model.MahjongTile
import io.github.cottonmc.cotton.gui.client.ScreenDrawing
import io.github.cottonmc.cotton.gui.widget.WWidget
import io.github.cottonmc.cotton.gui.widget.data.Color
import io.github.cottonmc.cotton.gui.widget.data.HorizontalAlignment
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.util.math.MatrixStack

class WBotFace(
    var code: Int? = null,
    var alignment: HorizontalAlignment = HorizontalAlignment.CENTER
) : WWidget() {

    override fun canResize(): Boolean = true

    @Environment(EnvType.CLIENT)
    override fun paint(matrices: MatrixStack, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val tile = code?.let { MahjongTile.values()[it] } ?: MahjongTile.UNKNOWN
        val boxWidth = height * 48 / 64   //配合牌的材質的寬高比, 高比較大, 以高為準, 寬用計算的
        val imgX = when (alignment) {
            HorizontalAlignment.LEFT -> x
            HorizontalAlignment.CENTER -> x + (width - boxWidth) / 2
            HorizontalAlignment.RIGHT -> x + width - boxWidth
        }
        ScreenDrawing.texturedRect(matrices, imgX, y, boxWidth, height, tile.surfaceIdentifier, Color.WHITE.toRgb())
    }
}