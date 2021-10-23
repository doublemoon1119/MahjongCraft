package doublemoon.mahjongcraft.client.gui.widget

import io.github.cottonmc.cotton.gui.widget.TooltipBuilder
import io.github.cottonmc.cotton.gui.widget.WTextField
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.text.Text


class WTooltipTextField(
    suggestion: Text?,
    var tooltip: Array<Text>
) : WTextField(suggestion) {
    @Environment(EnvType.CLIENT)
    override fun addTooltip(tooltip: TooltipBuilder) {
        tooltip.add(*this.tooltip)
    }
}