package doublemoon.mahjongcraft.client.gui.widget

import io.github.cottonmc.cotton.gui.widget.WPlainPanel
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.util.math.MatrixStack

/**
 * 在符合 [condition] 的條件下才會渲染的 [WPlainPanel]
 * */
class WConditionalPlainPanel(
    private val condition: () -> Boolean
) : WPlainPanel() {
    @Environment(EnvType.CLIENT)
    override fun paint(matrices: MatrixStack?, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        if (!condition.invoke()) return
        super.paint(matrices, x, y, mouseX, mouseY)
    }
}