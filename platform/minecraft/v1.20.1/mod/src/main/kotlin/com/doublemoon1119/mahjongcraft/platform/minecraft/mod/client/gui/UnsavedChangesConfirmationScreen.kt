package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * 離開仍有未套用變更的編輯畫面時共用的「套用並返回／放棄變更／繼續編輯」三選一確認畫面；
 * Client Config、HUD 版面編輯器與 Room 設定頁共用同一份，避免各自複製一份後各自漂移。
 *
 * @property previousScreen 選擇繼續編輯或按 Esc 時返回的畫面。
 * @property onApply 套用變更的動作；呼叫端也負責後續畫面切換。
 * @property onDiscard 放棄變更的動作；呼叫端也負責後續畫面切換。
 * @property changesPreview 套用按鈕的變更預覽提示；null 時不顯示提示。
 */
internal class UnsavedChangesConfirmationScreen(
    private val previousScreen: Screen,
    private val onApply: () -> Unit,
    private val onDiscard: () -> Unit,
    private val changesPreview: Component? = null,
) : Screen(Component.translatable(MinecraftClientConfigScreenKeys.UNSAVED_CHANGES_TITLE)) {
    /** 建立套用、放棄與繼續編輯三個按鈕；套用按鈕附上呼叫端提供的變更預覽。 */
    override fun init() {
        val buttonWidth = minOf(160, width - 24)
        val left = (width - buttonWidth) / 2
        addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftClientConfigScreenKeys.APPLY_AND_BACK)) { onApply() }
                .dimensions(left, height / 2, buttonWidth, 20).build().also {
                    it.tooltip = changesPreview?.let(Tooltip::create)
                },
        )
        addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftClientConfigScreenKeys.DISCARD_CHANGES)) { onDiscard() }
                .dimensions(left, height / 2 + 24, buttonWidth, 20).build(),
        )
        addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftClientConfigScreenKeys.CONTINUE_EDITING)) { minecraft?.setScreen(previousScreen) }
                .dimensions(left, height / 2 + 48, buttonWidth, 20).build(),
        )
    }

    /** 確認畫面不暫停遊戲。 */
    override fun isPauseScreen(): Boolean = false

    /** Esc 返回前一個畫面，避免無聲放棄變更。 */
    override fun onClose() {
        minecraft?.setScreen(previousScreen)
    }

    /** 繪製確認標題與說明。 */
    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        context.drawCenteredString(font, title, width / 2, height / 2 - 42, FormScreenLayout.TITLE_COLOR)
        context.drawCenteredString(
            font,
            Component.translatable(MinecraftClientConfigScreenKeys.UNSAVED_CHANGES_MESSAGE),
            width / 2,
            height / 2 - 26,
            0xFFFFFF,
        )
        super.render(context, mouseX, mouseY, delta)
    }
}
