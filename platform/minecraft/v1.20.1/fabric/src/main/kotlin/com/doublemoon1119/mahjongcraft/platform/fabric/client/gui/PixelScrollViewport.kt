package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

/** 以像素捲動的內容區：提供捲軸幾何與可捲動範圍，供 [PixelScrollState] 使用。 */
internal interface PixelScrollViewport {
    /**
     * 依內容總高度與目前像素偏移建立垂直捲軸。
     *
     * @param contentHeight 內容總高度，單位為像素。
     * @param scroll 目前內容偏移，單位為像素。
     * @return 以像素為單位的捲軸幾何。
     */
    fun scrollbar(contentHeight: Int, scroll: Double): ScrollbarLayout

    /**
     * 將內容偏移限制在可捲動範圍內。
     *
     * @param scroll 目前內容偏移，單位為像素。
     * @param contentHeight 內容總高度，單位為像素。
     * @return 限制後的內容偏移。
     */
    fun clampScroll(scroll: Double, contentHeight: Int): Double
}
