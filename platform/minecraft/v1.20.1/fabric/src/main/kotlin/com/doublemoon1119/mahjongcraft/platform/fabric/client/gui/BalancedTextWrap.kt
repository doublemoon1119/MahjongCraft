package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import net.minecraft.client.font.TextRenderer
import net.minecraft.text.OrderedText
import net.minecraft.text.StringVisitable

/**
 * 在不增加行數的前提下，找出能讓各行長度最接近的最小換行寬度，避免最後一行只剩一兩個字。
 *
 * @param maximumWidth 允許的最大換行寬度。
 * @param lineCount 以指定寬度換行時得到的行數。
 * @return 介於 1 與 [maximumWidth] 之間、行數與以 [maximumWidth] 換行時相同的最小寬度。
 */
internal fun balancedWrapWidth(maximumWidth: Int, lineCount: (Int) -> Int): Int {
    if (maximumWidth <= 1) return maximumWidth.coerceAtLeast(1)
    val target = lineCount(maximumWidth)
    if (target <= 1) return maximumWidth
    var low = 1
    var high = maximumWidth
    while (low < high) {
        val middle = (low + high) / 2
        if (lineCount(middle) <= target) high = middle else low = middle + 1
    }
    return high
}

/**
 * 以 [balancedWrapWidth] 找到的寬度換行；行數與一般換行相同，但各行長度較平均。
 *
 * @param text 要換行的文字；文字內的換行字元照常斷行。
 * @param maximumWidth 允許的最大換行寬度。
 * @return 換行後的每一行。
 */
internal fun TextRenderer.wrapLinesBalanced(text: StringVisitable, maximumWidth: Int): List<OrderedText> = wrapLines(text, balancedWrapWidth(maximumWidth) { width -> wrapLines(text, width).size })
