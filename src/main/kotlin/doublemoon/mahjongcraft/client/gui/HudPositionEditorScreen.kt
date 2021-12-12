package doublemoon.mahjongcraft.client.gui

import doublemoon.mahjongcraft.MOD_ID
import doublemoon.mahjongcraft.MahjongCraftClient
import doublemoon.mahjongcraft.client.gui.widget.WDraggablePlainPanel
import doublemoon.mahjongcraft.client.gui.widget.rootPlainPanel
import io.github.cottonmc.cotton.gui.client.BackgroundPainter
import io.github.cottonmc.cotton.gui.client.CottonClientScreen
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription
import io.github.cottonmc.cotton.gui.widget.WText
import io.github.cottonmc.cotton.gui.widget.data.Color
import io.github.cottonmc.cotton.gui.widget.data.HorizontalAlignment
import io.github.cottonmc.cotton.gui.widget.data.VerticalAlignment
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.text.TranslatableText

@Environment(EnvType.CLIENT)
class HudPositionEditorScreen(hud: MahjongCraftHud) : CottonClientScreen(HudPositionEditorGui(hud)) {
    override fun init() {
        super.init()
        (description as HudPositionEditorGui).reposition(width, height)
    }

    override fun onClose() {
        super.onClose()
        MahjongCraftClient.hud?.reposition()
    }
}

@Environment(EnvType.CLIENT)
class HudPositionEditorGui(
    private val hud: MahjongCraftHud
) : LightweightGuiDescription() {
    private val window = MinecraftClient.getInstance().window
    private val config = MahjongCraftClient.config
    private val quickActionsText = WText(TranslatableText("config.$MOD_ID.quick_actions"), Color.WHITE.toRgb()).apply {
        verticalAlignment = VerticalAlignment.CENTER
        horizontalAlignment = HorizontalAlignment.CENTER
    }
    private val quickActions = WDraggablePlainPanel(color = config.quickActions.hudAttribute.backgroundColor) { x, y ->
        with(config.quickActions.hudAttribute) {
            this.x = x / window.scaledWidth.toDouble()
            this.y = y / window.scaledHeight.toDouble()
        }
        MahjongCraftClient.saveConfig()
    }.also { it.add(quickActionsText, 0, 0, it.width, it.height) }

    init {
        fullscreen = true
        rootPlainPanel(width = 0, height = 0) { //fullscreen 可以不用輸入大小
            backgroundPainter = BackgroundPainter.createColorful((0x00_000000).toInt())
            this.add(quickActions, 0, 0)
        }
    }

    fun reposition(screenWidth: Int, screenHeight: Int) {
        val quickActions = MahjongCraftClient.config.quickActions
        hud.quickActionRootSize.also { (width, height) ->
            this.quickActions.setSize(width, height)
            quickActionsText.setSize(width, height)
        }
        quickActions.hudAttribute.also {
            this.quickActions.setLocation(
                (screenWidth * it.x).toInt(),
                (screenHeight * it.y).toInt()
            )
        }
    }
}