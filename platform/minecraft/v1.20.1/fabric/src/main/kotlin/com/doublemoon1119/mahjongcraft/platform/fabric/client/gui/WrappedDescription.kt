package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.StringVisitable
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Language
import java.util.Optional

/**
 * 將說明依可用寬度換行，保留各文字片段的樣式。
 *
 * @param text 說明全文。
 * @param width 可用寬度。
 * @return 各行的文字。
 */
internal fun TextRenderer.wrapDescription(text: Text, width: Int): List<StringVisitable> = textHandler.wrapLines(text, width, Style.EMPTY)

/**
 * 繪製可容納的說明行。內容超出高度時，末行使用同一個跑馬燈顯示其餘文字。
 *
 * @param context 畫面繪製環境。
 * @param renderer 文字繪製器。
 * @param lines 已依指定寬度換行的說明。
 * @param visibleLines 可用的行數。
 * @param left 說明左界。
 * @param top 說明頂端。
 * @param width 說明寬度。
 * @param color 文字顏色。
 * @param marquee 末行的跑馬燈進度。
 * @param subject 目前要捲動顯示的項目；沒有時為 null。
 */
internal fun drawWrappedDescription(
    context: DrawContext,
    renderer: TextRenderer,
    lines: List<StringVisitable>,
    visibleLines: Int,
    left: Int,
    top: Int,
    width: Int,
    color: Int,
    marquee: MarqueeState,
    subject: Any?,
) {
    val truncated = visibleLines in 1 until lines.size
    if (!truncated) marquee.offset(subject = null, overflow = 0)
    if (visibleLines == 0 || lines.isEmpty()) return

    context.enableScissor(left, top, left + width, top + minOf(visibleLines, lines.size) * renderer.fontHeight)
    try {
        lines.take(visibleLines).forEachIndexed { index, line ->
            val y = top + index * renderer.fontHeight
            if (truncated && index == visibleLines - 1) {
                MarqueeText.draw(
                    context = context,
                    textRenderer = renderer,
                    text = joinDescriptionTail(lines.drop(index)),
                    left = left,
                    width = width,
                    y = y,
                    color = color,
                    state = marquee,
                    subject = subject,
                )
            } else {
                context.drawText(renderer, Language.getInstance().reorder(line), left, y, color, false)
            }
        }
    } finally {
        context.disableScissor()
    }
}

/** 把最後可見行與後續文字接成單行，保留原本的文字樣式。 */
private fun joinDescriptionTail(lines: List<StringVisitable>): Text {
    val tail = Text.empty()
    lines.forEachIndexed { index, line ->
        if (index > 0) tail.append(" ")
        line.visit(
            { style, part ->
                tail.append(Text.literal(part).setStyle(style))
                Optional.empty<Unit>()
            },
            Style.EMPTY,
        )
    }
    return tail
}
