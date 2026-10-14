package com.doublemoon1119.mahjongcraft.platform.fabric.client

import net.minecraft.client.font.TextRenderer
import net.minecraft.text.Text

/**
 * 依客戶端字型實際寬度裁切文字，供不同 client 畫面共用相同的省略規則。
 *
 * @param textRenderer 目前 client 使用的字型測量器。
 * @param text 原始文字。
 * @param maximumWidth 可用的像素寬度。
 * @return 原始文字或附加省略號的裁切文字；寬度不為正時為空文字。
 */
internal fun fitClientText(
    textRenderer: TextRenderer,
    text: Text,
    maximumWidth: Int,
): Text = fitClientText(text, maximumWidth, textRenderer::getWidth)

/**
 * 以注入的寬度量測函式裁切文字，供沒有 client renderer 的測試驗證邊界。
 *
 * @param text 原始文字。
 * @param maximumWidth 可用的像素寬度。
 * @param width 寬度量測函式。
 * @return 原始文字或附加省略號的裁切文字。
 */
internal fun fitClientText(
    text: Text,
    maximumWidth: Int,
    width: (Text) -> Int,
): Text {
    if (maximumWidth <= 0) return Text.empty()
    if (width(text) <= maximumWidth) return text
    val raw = text.string
    val suffix = "..."
    var end = raw.length
    while (end > 0 && width(Text.literal(raw.substring(0, end) + suffix)) > maximumWidth) end--
    return Text.literal(raw.substring(0, end) + suffix)
}
