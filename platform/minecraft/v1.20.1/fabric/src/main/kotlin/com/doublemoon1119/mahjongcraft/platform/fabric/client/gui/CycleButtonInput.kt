package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftCycleButtonKeys
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * 循環切換按鈕的共用操作：左鍵選擇下一項，右鍵或按住 Shift 點擊選擇上一項。
 *
 * 選項至少三個時在提示框尾端說明兩種操作；只有兩個選項時往前往後結果相同，只說明左鍵；
 * 少於兩個選項時不說明。
 */
internal object CycleButtonInput {
    /** 需要同時說明往前與往後操作的最少選項數。 */
    const val MIN_OPTIONS_FOR_PREVIOUS_HINT: Int = 3

    /** 需要說明操作方式的最少選項數。 */
    const val MIN_OPTIONS_FOR_HINT: Int = 2

    /** 目前是否正在處理按鈕的右鍵點擊。 */
    private var secondaryClickActive: Boolean = false

    /**
     * 依這次點擊是右鍵或按住 Shift 決定方向。
     *
     * @return 右鍵或按住 Shift 時為 -1（上一項），否則為 1（下一項）。
     */
    fun step(): Int = direction(shiftHeld = Screen.hasShiftDown(), secondaryClick = secondaryClickActive)

    /**
     * 依按鍵狀態決定點擊的方向。
     *
     * @param shiftHeld 是否按住 Shift。
     * @param secondaryClick 是否為右鍵點擊。
     * @return 右鍵或按住 Shift 時為 -1（上一項），否則為 1（下一項）。
     */
    fun direction(shiftHeld: Boolean, secondaryClick: Boolean): Int = if (shiftHeld || secondaryClick) -1 else 1

    /**
     * 以右鍵點擊的身分執行按鈕動作；動作執行期間 [step] 回報往上一項。
     *
     * @param T 動作的回傳型別。
     * @param action 按鈕被右鍵點擊時執行的動作。
     * @return 動作的回傳值。
     */
    fun <T> duringSecondaryClick(action: () -> T): T {
        val previous = secondaryClickActive
        secondaryClickActive = true
        try {
            return action()
        } finally {
            secondaryClickActive = previous
        }
    }

    /**
     * 依方向取得下一個選項索引，頭尾相接。
     *
     * @param currentIndex 目前選項的索引；不在選項中時為 -1。
     * @param size 選項數量。
     * @param step 方向，1 為下一項、-1 為上一項。
     * @return 下一個選項的索引；目前選項不在範圍內時，往下從第一項開始、往上從最後一項開始。
     * @throws IllegalArgumentException [size] 不大於 0 時拋出。
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
     * @param T 選項型別。
     * @param options 依顯示順序排列的選項。
     * @param current 目前選項。
     * @param step 方向，1 為下一項、-1 為上一項。
     * @return 下一個選項。
     * @throws IllegalArgumentException [options] 為空時拋出。
     */
    fun <T> next(
        options: List<T>,
        current: T,
        step: Int = step(),
    ): T = options[nextIndex(currentIndex = options.indexOf(current), size = options.size, step = step)]

    /**
     * 在提示框尾端空一行後加上操作說明；選項少於兩個或按鈕停用時不加。
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
        val hints = hintTexts(optionCount = optionCount, active = active)
        if (hints.isEmpty()) return tooltip
        val result = Text.empty().append(tooltip).append("\n")
        hints.forEach { result.append("\n").append(it) }
        return result
    }

    /**
     * 逐行繪製的提示框版本：在最後空一行後加上操作說明；選項少於兩個或按鈕停用時不加。
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
        val hints = hintTexts(optionCount = optionCount, active = active)
        if (hints.isEmpty()) return lines
        return buildList {
            addAll(lines)
            add(Text.empty())
            addAll(hints)
        }
    }

    /**
     * 取得循環按鈕的操作說明。
     *
     * @param optionCount 選項數量。
     * @param active 按鈕是否可以操作。
     * @return 灰色的操作說明：至少三個選項時為往下與往上兩行，兩個選項時只有往下一行，其他情況為空。
     */
    fun hintTexts(optionCount: Int, active: Boolean = true): List<Text> = when {
        !active || optionCount < MIN_OPTIONS_FOR_HINT -> emptyList()
        optionCount < MIN_OPTIONS_FOR_PREVIOUS_HINT -> listOf(Text.translatable(MinecraftCycleButtonKeys.CLICK_NEXT).formatted(Formatting.GRAY))
        else -> listOf(
            Text.translatable(MinecraftCycleButtonKeys.CLICK_NEXT).formatted(Formatting.GRAY),
            Text.translatable(MinecraftCycleButtonKeys.CLICK_PREVIOUS).formatted(Formatting.GRAY),
        )
    }
}
