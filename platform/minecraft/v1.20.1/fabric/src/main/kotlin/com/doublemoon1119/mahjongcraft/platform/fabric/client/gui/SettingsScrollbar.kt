package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import net.minecraft.client.gui.DrawContext

/** 設定類畫面共用的垂直捲軸外觀。 */
internal object SettingsScrollbar {
    /** 軌道與滑塊的寬度。 */
    const val WIDTH: Int = 3

    /** 滑塊的最小高度。 */
    const val MINIMUM_THUMB_HEIGHT: Int = 12

    /** 軌道的 ARGB 顏色。 */
    private const val TRACK_COLOR: Int = 0x80505050.toInt()

    /** 滑塊的 ARGB 顏色。 */
    private const val THUMB_COLOR: Int = 0xFFD0D0D0.toInt()

    /**
     * 繪製捲軸；內容不需要捲動時不繪製。
     *
     * @param context 畫面繪製情境。
     * @param left 軌道左界。
     * @param layout 捲軸的軌道與滑塊位置。
     */
    fun render(context: DrawContext, left: Int, layout: ScrollbarLayout) {
        if (layout.maximumScroll <= 0) return
        context.fill(left, layout.trackTop, left + WIDTH, layout.trackBottom, TRACK_COLOR)
        context.fill(left, layout.thumbTop, left + WIDTH, layout.thumbTop + layout.thumbHeight, THUMB_COLOR)
    }
}
