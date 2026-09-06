package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text

/**
 * 離開仍有未套用變更的編輯畫面時共用的「套用並返回／放棄變更／繼續編輯」三選一確認畫面；
 * Client Config、HUD 版面編輯器與 Room 設定頁共用同一份，避免各自複製一份後各自漂移。
 */
internal class UnsavedChangesConfirmationScreen(
    private val previousScreen: Screen,
    private val onApply: () -> Unit,
    private val onDiscard: () -> Unit,
    private val changesPreview: Text? = null,
) : Screen(Text.translatable(MinecraftClientConfigScreenKeys.UNSAVED_CHANGES_TITLE)) {
    /** 建立套用、放棄與繼續編輯三個按鈕；套用按鈕附上呼叫端提供的變更預覽。 */
    override fun init() {
        val buttonWidth = minOf(160, width - 24)
        val left = (width - buttonWidth) / 2
        addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.APPLY_AND_BACK)) { onApply() }
                .dimensions(left, height / 2, buttonWidth, 20).build().also {
                    it.tooltip = changesPreview?.let(Tooltip::of)
                },
        )
        addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.DISCARD_CHANGES)) { onDiscard() }
                .dimensions(left, height / 2 + 24, buttonWidth, 20).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.CONTINUE_EDITING)) { client?.setScreen(previousScreen) }
                .dimensions(left, height / 2 + 48, buttonWidth, 20).build(),
        )
    }

    /** 確認畫面不暫停遊戲。 */
    override fun shouldPause(): Boolean = false

    /** Esc 返回前一個畫面，避免無聲放棄變更。 */
    override fun close() {
        client?.setScreen(previousScreen)
    }

    /** 繪製確認標題與說明。 */
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, 0xAA000000.toInt())
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 42, 0xFFD54F)
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable(MinecraftClientConfigScreenKeys.UNSAVED_CHANGES_MESSAGE),
            width / 2,
            height / 2 - 26,
            0xFFFFFF,
        )
        super.render(context, mouseX, mouseY, delta)
    }
}
