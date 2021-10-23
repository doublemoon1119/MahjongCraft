package doublemoon.mahjongcraft.client.gui.widget

import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription
import io.github.cottonmc.cotton.gui.widget.WTabPanel
import io.github.cottonmc.cotton.gui.widget.WWidget
import io.github.cottonmc.cotton.gui.widget.icon.Icon
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.text.Text


/**
 * 將 [WTabPanel] 作為根使用
 * */
@Environment(EnvType.CLIENT)
fun LightweightGuiDescription.rootTabPanel(
    width: Int,
    height: Int,
    init: WTabPanel.() -> Unit
): WTabPanel {
    val panel = WTabPanel().also {
        it.setSize(width, height)
        it.init()
    }
    rootPanel = panel
    rootPanel.validate(this)
    return panel
}

/**
 * [WTabPanel.Tab.Builder],
 * [title] 和 [icon] 不能同時為 null
 * */
@Environment(EnvType.CLIENT)
fun WTabPanel.tab(
    widget: WWidget,
    title: Text? = null,
    icon: Icon? = null,
    tooltip: Collection<Text>? = null
): WTabPanel.Tab {
    val tab = WTabPanel.Tab.Builder(widget).also { builder ->
        title?.also { builder.title(it) }
        icon?.also { builder.icon(it) }
        tooltip?.also { builder.tooltip(it) }
    }.build()
    this.add(tab)
    return tab
}