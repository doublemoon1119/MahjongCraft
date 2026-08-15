package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import kotlinx.serialization.json.Json
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text

/**
 * 遊戲規則設定的文字輸入畫面：文字框預填目前設定的 JSON，「套用」送出後保持開啟、「確認」送出後關閉，
 * ESC 直接關閉、不送出任何東西（vanilla [Screen] 內建行為）。
 *
 * TODO: 這是本 mod 第一個 GUI 畫面，先直接用 vanilla widget 拼、不預先設計一套共用 UI 元件庫。之後如果
 *   新增第二個畫面，應該回頭比對兩者、抽出共用的設計慣例（配色、間距、按鈕風格等），不要各自寫一份。
 */
class GameConfigScreen(
    private val initialConfigJson: String,
    private val json: Json,
) : Screen(Text.translatable(MinecraftMessageKeys.GAME_CONFIG_SCREEN_TITLE)) {
    private lateinit var textField: TextFieldWidget

    override fun init() {
        textField = TextFieldWidget(textRenderer, width / 2 - 150, height / 2 - 30, 300, 20, Text.empty())
        textField.setMaxLength(MAX_CONFIG_LENGTH)
        textField.text = initialConfigJson
        addDrawableChild(textField)
        setInitialFocus(textField)

        addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftMessageKeys.GAME_CONFIG_SCREEN_APPLY)) { submit(close = false) }
                .dimensions(width / 2 - 150, height / 2, 145, 20)
                .build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftMessageKeys.GAME_CONFIG_SCREEN_CONFIRM)) { submit(close = true) }
                .dimensions(width / 2 + 5, height / 2, 145, 20)
                .build(),
        )
    }

    /** 把文字框目前內容送給伺服器；[close] 為 true 時等同「確認」，送出後關閉畫面。 */
    private fun submit(close: Boolean) {
        MahjongChannels.updateGameConfig.sendToServer(json, textField.text)
        if (close) close()
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        super.render(context, mouseX, mouseY, delta)
    }

    override fun shouldPause(): Boolean = false

    private companion object {
        /** 遠超伺服器端 payload 上限（[com.doublemoon1119.mahjongcraft.platform.fabric.network.S2CChannel]
         * 同一個常數），文字框本身不應該是這裡的瓶頸。 */
        const val MAX_CONFIG_LENGTH: Int = 1 shl 16
    }
}
