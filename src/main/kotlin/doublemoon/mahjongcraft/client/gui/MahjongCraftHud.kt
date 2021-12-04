package doublemoon.mahjongcraft.client.gui

import doublemoon.mahjongcraft.MahjongCraftClient
import doublemoon.mahjongcraft.client.ModConfig
import doublemoon.mahjongcraft.client.gui.widget.WConditionalPlainPanel
import io.github.cottonmc.cotton.gui.client.BackgroundPainter
import io.github.cottonmc.cotton.gui.client.CottonHud
import io.github.cottonmc.cotton.gui.widget.WDynamicLabel
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.text.TranslatableText

/**
 * 請參考 [CottonHud]
 * */
@Environment(EnvType.CLIENT)
class MahjongCraftHud(
    config: ModConfig
) {
    private val client = MinecraftClient.getInstance()
    private val textRenderer = client.textRenderer
    private val fontHeight = textRenderer.fontHeight
    private val quickActions = config.quickActions
    private val quickActionsRoot =
        WConditionalPlainPanel { MahjongCraftClient.playing && quickActions.displayHudWhenPlaying }
    private val quickActionsTextAndLabel = listOf(
        { colorPrefix(quickActions.autoArrange) + AUTO_ARRANGE.string },
        { colorPrefix(quickActions.autoCallWin) + AUTO_CALL_WIN.string },
        { colorPrefix(quickActions.noChiiPonKan) + NO_CHII_PON_KAN.string },
        { colorPrefix(quickActions.autoDrawAndDiscard) + AUTO_DRAW_AND_DISCARD.string }
    ).associateWith { WDynamicLabel(it) }

    init {
        initQuickActionsWidgets()
        refresh()
    }

    fun refresh() {
        val window = client.window
        val width = window.scaledWidth
        val height = window.scaledHeight
        resetQuickActionsWidgetsSizeAndLocation(width, height)
    }

    private fun initQuickActionsWidgets() {
        quickActionsTextAndLabel.forEach { (_, label) -> quickActionsRoot.add(label, 0, 0, 0, 0) }
        quickActionsRoot.backgroundPainter = BackgroundPainter.createColorful(0x7f271c1d)
        setQuickActionsRootSize()
    }

    private fun resetQuickActionsWidgetsSizeAndLocation(width: Int, height: Int) {
        quickActionsTextAndLabel.forEach { (text, label) ->
            val index = quickActionsTextAndLabel.values.indexOf(label)
            label.setSize(textWidth(text.invoke()), fontHeight)
            label.setLocation(INSET, INSET + index * (fontHeight + LABEL_INTERVAL))
        }
        setQuickActionsRootSize()
        val hudPosition = quickActions.hudPosition
        val x = (width * hudPosition.x).toInt()
        val y = (height * hudPosition.y - quickActionsRoot.height / 2).toInt()
        CottonHud.add(quickActionsRoot, x, y)
    }

    private fun setQuickActionsRootSize() {
        val width = INSET * 2 + quickActionsTextAndLabel.maxOf { it.value.width }
        val height = INSET * 2 + quickActionsTextAndLabel.values.size * (fontHeight + LABEL_INTERVAL) - LABEL_INTERVAL
        quickActionsRoot.setSize(width, height)
    }

    private fun colorPrefix(settingEnabled: Boolean) = if (settingEnabled) "§a" else "§c"

    private fun textWidth(text: String) = textRenderer.getWidth(text)

    companion object {
        private const val INSET = 6
        private const val LABEL_INTERVAL = 2
        private val AUTO_ARRANGE = TranslatableText("text.autoconfig.mahjongcraft.option.quickActions.autoArrange")
        private val AUTO_CALL_WIN = TranslatableText("text.autoconfig.mahjongcraft.option.quickActions.autoCallWin")
        private val NO_CHII_PON_KAN = TranslatableText("text.autoconfig.mahjongcraft.option.quickActions.noChiiPonKan")
        private val AUTO_DRAW_AND_DISCARD =
            TranslatableText("text.autoconfig.mahjongcraft.option.quickActions.autoDrawAndDiscard")
    }
}
