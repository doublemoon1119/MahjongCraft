package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

/** 截斷後補在尾端的省略號。 */
internal const val PREVIEW_ELLIPSIS = "…"

/**
 * 依可用寬度截斷 HUD 預覽框內的文字，放得下時原樣回傳。
 *
 * 寬度量測由呼叫端以 [widthOf] 注入，因此這段規則不依賴 Minecraft 的文字繪製器，可直接在 JVM 測試中驗證。
 * 連省略號本身都放不下時只回傳省略號，確保結果永遠不超過 [maxWidth] 以外的內容被畫出來。
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
