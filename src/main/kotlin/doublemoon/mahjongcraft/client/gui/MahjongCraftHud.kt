package doublemoon.mahjongcraft.client.gui

import doublemoon.mahjongcraft.client.ModConfig
import io.github.cottonmc.cotton.gui.client.BackgroundPainter
import io.github.cottonmc.cotton.gui.client.CottonHud
import io.github.cottonmc.cotton.gui.widget.WDynamicLabel
import io.github.cottonmc.cotton.gui.widget.WPlainPanel
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
    private val root = WPlainPanel()
    private val textAndLabel = listOf(
        { colorPrefix(quickActions.autoArrange) + AUTO_ARRANGE.string },
        { colorPrefix(quickActions.autoCallWin) + AUTO_CALL_WIN.string },
        { colorPrefix(quickActions.noChiiPonKan) + NO_CHII_PON_KAN.string },
        { colorPrefix(quickActions.autoDrawAndDiscard) + AUTO_DRAW_AND_DISCARD.string }
    ).associateWith { WDynamicLabel(it) }

    init {
        textAndLabel.forEach { (text, label) ->
            val index = textAndLabel.values.indexOf(label)
            root.add(
                label,
                ORIGIN_X,
                ORIGIN_Y + index * (fontHeight + LABEL_INTERVAL),
                textRenderer.getWidth(text.invoke()),
                fontHeight
            )
        }
        root.backgroundPainter = BackgroundPainter.createColorful(0x7f271c1d)
        setRootSize()
        refresh()
    }

    fun refresh() {
        if (quickActions.displayHud) show() else hide()
    }

    private fun colorPrefix(settingEnabled: Boolean) = if (settingEnabled) "§a" else "§c"

    private fun show() {
        textAndLabel.forEach { (text, label) ->
            val index = textAndLabel.values.indexOf(label)
            label.setSize(textRenderer.getWidth(text.invoke()), fontHeight)
            label.setLocation(ORIGIN_X, ORIGIN_Y + index * (fontHeight + LABEL_INTERVAL))
        }
        setRootSize()
        val window = client.window
        val hudHeight = window.scaledHeight
        CottonHud.add(root, 0, (hudHeight * 0.7 - root.height / 2).toInt())
    }

    private fun hide() {
        CottonHud.remove(root)
    }

    private fun setRootSize() {
        val width = ORIGIN_X * 2 + textAndLabel.maxOf { it.value.width }
        val height = ORIGIN_Y * 2 + textAndLabel.values.size * (fontHeight + LABEL_INTERVAL) - LABEL_INTERVAL
        root.setSize(width, height)
    }

    companion object {
        private const val ORIGIN_X = 6
        private const val ORIGIN_Y = 6
        private const val LABEL_INTERVAL = 2
        private val AUTO_ARRANGE = TranslatableText("text.autoconfig.mahjongcraft.option.quickActions.autoArrange")
        private val AUTO_CALL_WIN = TranslatableText("text.autoconfig.mahjongcraft.option.quickActions.autoCallWin")
        private val NO_CHII_PON_KAN = TranslatableText("text.autoconfig.mahjongcraft.option.quickActions.noChiiPonKan")
        private val AUTO_DRAW_AND_DISCARD =
            TranslatableText("text.autoconfig.mahjongcraft.option.quickActions.autoDrawAndDiscard")
    }
}
