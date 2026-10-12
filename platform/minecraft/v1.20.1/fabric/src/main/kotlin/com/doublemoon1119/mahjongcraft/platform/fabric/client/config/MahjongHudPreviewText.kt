package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

/** 截斷後補在尾端的省略號。 */
internal const val PREVIEW_ELLIPSIS = "…"

/**
 * 依可用寬度截斷 HUD 預覽框內的文字，放得下時原樣回傳。
 *
 * 寬度量測由呼叫端以 [widthOf] 注入，因此這段規則不依賴 Minecraft 的文字繪製器，可直接在 JVM 測試中驗證。
 * 若省略號本身已達可用寬度，仍回傳省略號；其他情況才逐字縮短原文，直到原文前綴加省略號可放入寬度。
 *
 * @param text 原始文字。
 * @param maxWidth 可用像素寬度。
 * @param widthOf 測量字串像素寬度的函式。
 * @return 原文或以省略號結尾的截斷文字；省略號本身可能超過可用寬度。
 */
internal fun trimPreviewText(
    text: String,
    maxWidth: Int,
    widthOf: (String) -> Int,
): String {
    if (widthOf(text) <= maxWidth) return text
    val ellipsisWidth = widthOf(PREVIEW_ELLIPSIS)
    if (maxWidth <= ellipsisWidth) return PREVIEW_ELLIPSIS
    var end = text.length
    while (end > 0 && widthOf(text.substring(0, end)) + ellipsisWidth > maxWidth) end--
    return text.substring(0, end) + PREVIEW_ELLIPSIS
}
