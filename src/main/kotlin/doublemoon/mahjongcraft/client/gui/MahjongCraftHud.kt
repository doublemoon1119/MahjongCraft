package doublemoon.mahjongcraft.client.gui

import doublemoon.mahjongcraft.MOD_ID
import doublemoon.mahjongcraft.MahjongCraftClient
import doublemoon.mahjongcraft.client.gui.widget.WTileHints
import io.github.cottonmc.cotton.gui.client.BackgroundPainter
import io.github.cottonmc.cotton.gui.client.CottonHud
import io.github.cottonmc.cotton.gui.widget.WDynamicLabel
import io.github.cottonmc.cotton.gui.widget.WPlainPanel
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text

/**
 * 請參考 [CottonHud]
 * */
@Environment(EnvType.CLIENT)
class MahjongCraftHud {
    val quickActionsRootSize: Pair<Int, Int> get() = quickActionsRoot.let { it.width to it.height }
    val tileHintsRootSize: Pair<Int, Int> get() = tileHintsRoot.let { it.width to it.height }
    private val config = MahjongCraftClient.config
    private val client = MinecraftClient.getInstance()
    private val window = client.window
    private val textRenderer = client.textRenderer
    private val fontHeight = textRenderer.fontHeight
    private val quickActions = config.quickActions
    private val quickActionsRoot = WPlainPanel()
    private val quickActionsTextAndLabel = listOf(
        { colorPrefix(quickActions.autoArrange) + AUTO_ARRANGE.string },
        { colorPrefix(quickActions.autoCallWin) + AUTO_CALL_WIN.string },
        { colorPrefix(quickActions.noChiiPonKan) + NO_CHII_PON_KAN.string },
        { colorPrefix(quickActions.autoDrawAndDiscard) + AUTO_DRAW_AND_DISCARD.string }
    ).associateWith { WDynamicLabel(it) }
    private val tileHints = config.tileHints
    private val tileHintsRoot = WTileHints(tileHints)

    init {
        initQuickActions()
        refresh()
        reposition()
    }

    fun refresh() {
        quickActionsRoot.backgroundPainter = BackgroundPainter.createColorful(quickActions.hudAttribute.backgroundColor)
        tileHintsRoot.backgroundPainter = BackgroundPainter.createColorful(tileHints.hudAttribute.backgroundColor)
        if (MahjongCraftClient.playing) {
            quickActionsRoot.also { if (quickActions.displayHudWhenPlaying) CottonHud.add(it) else CottonHud.remove(it) }
        } else {
            CottonHud.remove(quickActionsRoot)
        }
        tileHintsRoot.also { if (tileHints.displayHud) CottonHud.add(it) else CottonHud.remove(it) }
    }

    fun reposition() {
        val width = window.scaledWidth
        val height = window.scaledHeight
        repositionQuickActions(width, height)
        repositionTileHints(width, height)
    }

    private fun initQuickActions() {
        quickActionsTextAndLabel.forEach { (_, label) -> quickActionsRoot.add(label, 0, 0, 0, 0) }
    }

    private fun repositionQuickActions(width: Int, height: Int) {
        quickActionsTextAndLabel.forEach { (text, label) ->
            val index = quickActionsTextAndLabel.values.indexOf(label)
            label.setSize(textWidth(text.invoke()), fontHeight)
            label.setLocation(INSET, INSET + index * (fontHeight + LABEL_INTERVAL))
        }
        //Root Size
        val widgetWidth = INSET * 2 + quickActionsTextAndLabel.maxOf { it.value.width }
        val widgetHeight =
            INSET * 2 + quickActionsTextAndLabel.values.size * (fontHeight + LABEL_INTERVAL) - LABEL_INTERVAL
        quickActionsRoot.setSize(widgetWidth, widgetHeight)
        //Root Location
        val (hudX, hudY) = quickActions.hudAttribute
        val x = (width * hudX).toInt()
        val y = (height * hudY).toInt()
        quickActionsRoot.setLocation(x, y)
    }

    private fun repositionTileHints(width: Int, height: Int) {
        tileHintsRoot.reposition()
        val x = ((width - tileHintsRoot.width) / 2.0).toInt()
        val y = (height * tileHints.hudAttribute.y).toInt()
        tileHintsRoot.setLocation(x, y)
    }

    private fun colorPrefix(settingEnabled: Boolean) = if (settingEnabled) "§a" else "§c"

    private fun textWidth(text: String) = textRenderer.getWidth(text)

    companion object {
        private const val INSET = 6
        private const val LABEL_INTERVAL = 2
        private val AUTO_ARRANGE = Text.translatable("config.$MOD_ID.quick_actions.auto_arrange")
        private val AUTO_CALL_WIN = Text.translatable("config.$MOD_ID.quick_actions.auto_call_win")
        private val NO_CHII_PON_KAN = Text.translatable("config.$MOD_ID.quick_actions.no_chii_pon_kan")
        private val AUTO_DRAW_AND_DISCARD = Text.translatable("config.$MOD_ID.quick_actions.auto_draw_and_discard")
    }
}
