package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.events.ContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import kotlin.time.Duration.Companion.seconds

/** 等畫面打開、切換或關閉的最長時間。 */
internal val SCREEN_TIMEOUT = 5.seconds

/** 打開或切換畫面後讓畫面完成一次繪製的刻數，只影響截圖時機。 */
internal const val SCREEN_SETTLE_TICKS = 2

/**
 * 等目前畫面換成指定類別的畫面。
 *
 * @param className 畫面類別名稱。
 * @return 在時限內出現時為 true。
 */
internal suspend fun AutotestContext.waitForScreen(className: String): Boolean = waitUntil(SCREEN_TIMEOUT) { client.screen?.javaClass?.simpleName == className }

/**
 * 按下目前畫面中文字含有指定翻譯鍵的第一個可按的按鈕，與玩家點擊相同。
 *
 * @param key 按鈕文字的翻譯鍵；文字由多段組成時，其中一段使用這個翻譯鍵也算。
 * @return 找到並按下時為 true。
 */
internal fun AutotestContext.pressButton(key: String): Boolean {
    val button = screenButtons().firstOrNull { it.active && it.message.usesKey(key) } ?: return false
    button.onPress()
    return true
}

/**
 * 按下目前畫面中文字完全等於指定文字的第一個可按的按鈕，用於只顯示符號、不使用翻譯鍵的按鈕。
 *
 * @param text 按鈕顯示的文字。
 * @return 找到並按下時為 true。
 */
internal fun AutotestContext.pressButtonShowing(text: String): Boolean {
    val button = screenButtons().firstOrNull { it.active && it.message.string == text } ?: return false
    button.onPress()
    return true
}

/**
 * 目前畫面中的全部按鈕，包含清單、分頁等容器中的按鈕，依畫面中的順序排列。
 *
 * @return 按鈕；沒有畫面時為空。
 */
internal fun AutotestContext.screenButtons(): List<AbstractButton> = client.screen?.let(::buttonsIn).orEmpty()

/**
 * 元件與其子元件中的全部按鈕。
 *
 * @param listener 畫面或容器元件。
 * @return 按鈕。
 */
private fun buttonsIn(listener: GuiEventListener): List<AbstractButton> = listOfNotNull(listener as? AbstractButton) + ((listener as? ContainerEventHandler)?.children()?.flatMap(::buttonsIn).orEmpty())

/**
 * 文字或其中任何一段是否使用指定的翻譯鍵。
 *
 * @param key 翻譯鍵。
 * @return 使用時為 true。
 */
private fun Component.usesKey(key: String): Boolean = (contents as? TranslatableContents)?.key == key || siblings.any { it.usesKey(key) }
