package doublemoon.mahjongcraft.client.gui.widget

import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongTile
import io.github.cottonmc.cotton.gui.client.ScreenDrawing
import io.github.cottonmc.cotton.gui.widget.WWidget
import io.github.cottonmc.cotton.gui.widget.data.Color
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.RotationAxis

class WMahjongTile(
    var mahjongTile: MahjongTile,
    var direction: TileDirection = TileDirection.NORMAL,
) : WWidget() {

    override fun canResize(): Boolean = true

    override fun setSize(x: Int, y: Int) {
        if (direction == TileDirection.NORMAL) {
            super.setSize(x, y)
        } else {
            this.width = y
            this.height = x
        }
    }

    @Environment(EnvType.CLIENT)
    override fun paint(matrices: MatrixStack, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        if (direction == TileDirection.NORMAL) {
            ScreenDrawing.texturedRect(
                matrices,
                x,
                y,
                width,
                height,
                mahjongTile.surfaceIdentifier,
                Color.WHITE.toRgb()
            )
        } else {
            //這裡的 height 跟 width 都是反著用的, 因為我們把 matrices 轉成橫的才把直的印上去, 所以應該套用原本直的高跟寬
            matrices.push()
            when (direction) {
                TileDirection.RIGHT -> {
                    matrices.translate((x + width).toDouble(), y.toDouble(), 0.0)
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90f))
                }
                TileDirection.LEFT -> {
                    matrices.translate(x.toDouble(), (y + height).toDouble(), 0.0)
                    matrices.multiply(RotationAxis.NEGATIVE_Z.rotationDegrees(90f))
                }
                else -> return //這種情況不會發生
            }
            ScreenDrawing.texturedRect(
                matrices,
                0, //x, y 在前面 when 的時候使用 translate 偏移過了, 直接用 0
                0,
                height,
                width,
                mahjongTile.surfaceIdentifier,
                Color.WHITE.toRgb()
            )
            matrices.pop()
        }
    }

    enum class TileDirection {
        LEFT, NORMAL, RIGHT
    }
}