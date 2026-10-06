package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftCycleButtonKeys
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * 循環切換按鈕的共用操作：點擊選擇下一項，按住 Shift 點擊選擇上一項。
 *
 * 選項至少三個時才在提示框尾端說明這兩種操作；只有兩個選項時往前往後結果相同，不另外說明。
 */
internal object CycleButtonInput {
    /** 需要說明操作方式的最少選項數。 */
    const val MIN_OPTIONS_FOR_HINT: Int = 3

    /**
     * 依目前是否按住 Shift 決定這次點擊的方向。
     *
     * @return 按住 Shift 時為 -1（上一項），否則為 1（下一項）。
     */
    fun step(): Int = if (Screen.hasShiftDown()) -1 else 1

    /**
     * 依方向取得下一個選項索引，頭尾相接。
     *
     * @param currentIndex 目前選項的索引；不在選項中時為 -1。
     * @param size 選項數量。
     * @param step 方向，1 為下一項、-1 為上一項。
     * @return 下一個選項的索引；目前選項不在範圍內時，往下從第一項開始、往上從最後一項開始。
     */
    fun nextIndex(
        currentIndex: Int,
        size: Int,
        step: Int,
    ): Int {
        require(size > 0) { "Cycle button must have at least one option" }
        if (currentIndex !in 0 until size) return if (step >= 0) 0 else size - 1
        return Math.floorMod(currentIndex + step, size)
    }

    /**
     * 依方向從選項清單取得下一個選項。
     *
     * @param options 依顯示順序排列的選項。
     * @param current 目前選項。
     * @param step 方向，1 為下一項、-1 為上一項。
     * @return 下一個選項。
     */
    fun <T> next(
        options: List<T>,
        current: T,
        step: Int = step(),
    ): T = options[nextIndex(currentIndex = options.indexOf(current), size = options.size, step = step)]

    /**
     * 在提示框尾端空一行後加上操作說明；選項少於三個或按鈕停用時不加。
     *
     * @param tooltip 原有的提示內容。
     * @param optionCount 選項數量。
     * @param active 按鈕是否可以操作。
     * @return 加上操作說明（或維持原樣）的提示內容。
     */
    fun withHint(
        tooltip: Text,
        optionCount: Int,
        active: Boolean = true,
    ): Text {
        if (!active || optionCount < MIN_OPTIONS_FOR_HINT) return tooltip
        return Text.empty().append(tooltip).append("\n\n").append(hintLines())
    }

    /**
     * 逐行繪製的提示框版本：在最後空一行後加上兩行操作說明；選項少於三個或按鈕停用時不加。
     *
     * @param lines 原有提示的每一行。
     * @param optionCount 選項數量。
     * @param active 按鈕是否可以操作。
     * @return 加上操作說明（或維持原樣）的提示行。
     */
    fun withHintLines(
        lines: List<Text>,
        optionCount: Int,
        active: Boolean = true,
    ): List<Text> {
        if (!active || optionCount < MIN_OPTIONS_FOR_HINT) return lines
        return lines + Text.empty() + hintTexts()
    }

    /**
     * 兩行操作說明，以換行串成一段文字。
     *
     * @return 灰色的「點擊選擇下一項」與「Shift＋點擊選擇上一項」。
     */
    private fun hintLines(): MutableText = Text.empty().also { result ->
        hintTexts().forEachIndexed { index, line ->
            if (index > 0) result.append("\n")
            result.append(line)
        }
    }

    /**
     * 兩行操作說明。
     *
     * @return 灰色的「點擊選擇下一項」與「Shift＋點擊選擇上一項」。
     */
    private fun hintTexts(): List<Text> = listOf(
        Text.translatable(MinecraftCycleButtonKeys.CLICK_NEXT).formatted(Formatting.GRAY),
        Text.translatable(MinecraftCycleButtonKeys.SHIFT_CLICK_PREVIOUS).formatted(Formatting.GRAY),
    )
}
