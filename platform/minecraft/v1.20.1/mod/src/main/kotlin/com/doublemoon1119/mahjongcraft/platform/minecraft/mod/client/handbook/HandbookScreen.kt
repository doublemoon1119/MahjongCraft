package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.handbook

import com.doublemoon1119.mahjongcraft.platform.minecraft.handbook.MinecraftHandbookScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.fitClientText
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScreenRendering
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScrollState
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * 麻將手冊快捷畫面：一列一個入口，點入口開啟對應的功能頁，功能頁返回時回到這裡。
 *
 * 關閉與 Esc 回到遊戲。入口放不下時以滾輪或捲軸捲動。
 *
 * @property entries 依顯示順序排列的入口。
 * @property openHint 底部開啟方式的提示，依目前的按鍵綁定產生。
 */
internal class HandbookScreen(
    private val entries: List<HandbookEntry>,
    private val openHint: () -> Component,
) : Screen(Component.translatable(MinecraftHandbookScreenKeys.TITLE)) {
    /** 入口清單的捲動位置。 */
    private val scroll = ScrollState()

    override fun init() {
        clearWidgets()
        scroll.clamp(layout().maximumScroll)
        val layout = layout()
        entries.drop(scroll.index).take(layout.visibleCount).forEachIndexed { visibleIndex, entry ->
            addRenderableWidget(
                HandbookEntryButton(
                    x = layout.entryLeft,
                    y = layout.entryTop(visibleIndex),
                    width = layout.entryWidth,
                    height = HandbookScreenLayout.ENTRY_HEIGHT,
                    entry = entry,
                    onPress = { entry.open(this) },
                ),
            )
        }
        val closeX = layout.footer.buttonX(HandbookScreenLayout.CLOSE)
        val closeWidth = layout.footer.buttonWidth(HandbookScreenLayout.CLOSE)
        addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHandbookScreenKeys.CLOSE)) { onClose() }
                .dimensions(closeX, layout.footerY, closeWidth, BUTTON_HEIGHT)
                .build(),
        )
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        val layout = layout()
        if (layout.isOverScrollableArea(mouseX, mouseY) && scroll.scrollBy(amount, layout.maximumScroll)) {
            init()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val layout = layout()
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && layout.isOverScrollbar(mouseX, mouseY)) {
            if (scroll.beginDrag(mouseY, layout.scrollbar)) init()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && scroll.dragging) {
            if (scroll.dragTo(mouseY, layout().scrollbar)) init()
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val handled = button == GLFW.GLFW_MOUSE_BUTTON_LEFT && scroll.dragging
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) scroll.endDrag()
        return handled || super.mouseReleased(mouseX, mouseY, button)
    }

    override fun isPauseScreen(): Boolean = false

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        val layout = layout()
        ScreenRendering.title(context, font, fitClientText(font, title, layout.contentWidth), width)
        val bar = layout.scrollbar
        if (bar.maximumScroll > 0) VerticalScrollbar.render(context, layout.scrollbarX, bar)
        if (layout.showsOpenHint) {
            context.drawCenteredString(
                font,
                fitClientText(font, openHint(), layout.contentWidth),
                width / 2,
                layout.openHintY,
                HINT_COLOR,
            )
        }
        super.render(context, mouseX, mouseY, delta)
    }

    /** 建立所有繪製與輸入共用的版面。 */
    private fun layout(): HandbookScreenLayout = HandbookScreenLayout(width, height, entries.size, scroll.index)

    /** 尺寸與顏色。 */
    private companion object {
        /** 按鈕高度。 */
        const val BUTTON_HEIGHT = 20

        /** 開啟方式提示的顏色。 */
        const val HINT_COLOR = 0xAAAAAA
    }
}
