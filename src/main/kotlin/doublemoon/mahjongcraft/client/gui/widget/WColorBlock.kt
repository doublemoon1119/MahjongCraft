package doublemoon.mahjongcraft.client.gui.widget

import io.github.cottonmc.cotton.gui.client.ScreenDrawing
import io.github.cottonmc.cotton.gui.widget.WWidget
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.util.math.MatrixStack

class WColorBlock(
    private val color: Int
) : WWidget() {

    override fun canResize(): Boolean = true

    @Environment(EnvType.CLIENT)
    override fun paint(matrices: MatrixStack?, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        ScreenDrawing.coloredRect(matrices, x, y, width, height, color)
    }
}