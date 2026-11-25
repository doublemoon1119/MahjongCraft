package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui

import net.minecraft.client.gui.GuiGraphics

/** 各畫面共用的垂直捲軸外觀。水平捲軸 [HorizontalScrollbar] 使用同樣的顏色。 */
internal object VerticalScrollbar {
    /** 軌道與滑塊的寬度。 */
    const val WIDTH: Int = 3

    /** 滑塊的最小高度。 */
    const val MINIMUM_THUMB_HEIGHT: Int = 12

    /** 軌道的 ARGB 顏色。 */
    const val TRACK_COLOR: Int = 0x80505050.toInt()

    /** 滑塊的 ARGB 顏色。 */
    const val THUMB_COLOR: Int = 0xFFD0D0D0.toInt()

    /**
     * 在畫面保留給捲軸的水平範圍內置中繪製捲軸。內容不需要捲動時不繪製。
     *
     * 保留的範圍通常比捲軸寬，讓游標不必精確壓在捲軸上就能拖曳。
     *
     * @param context 畫面繪製情境。
     * @param left 保留範圍的左界。
     * @param right 保留範圍的右界。
     * @param layout 捲軸的軌道與滑塊位置。
     */
    fun renderWithin(context: GuiGraphics, left: Int, right: Int, layout: ScrollbarLayout) {
        render(context, left + (right - left - WIDTH).coerceAtLeast(0) / 2, layout)
    }

    /**
     * 繪製捲軸。內容不需要捲動時不繪製。
     *
     * @param context 畫面繪製情境。
     * @param left 軌道左界。
     * @param layout 捲軸的軌道與滑塊位置。
     */
    fun render(context: GuiGraphics, left: Int, layout: ScrollbarLayout) {
        if (layout.maximumScroll <= 0) return
        context.fill(left, layout.trackTop, left + WIDTH, layout.trackBottom, TRACK_COLOR)
        context.fill(left, layout.thumbTop, left + WIDTH, layout.thumbTop + layout.thumbHeight, THUMB_COLOR)
    }
}
