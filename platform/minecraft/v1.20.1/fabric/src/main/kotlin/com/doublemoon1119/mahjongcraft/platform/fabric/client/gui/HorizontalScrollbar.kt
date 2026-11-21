package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import net.minecraft.client.gui.GuiGraphics

/** 各畫面共用的水平捲軸外觀，顏色與 [VerticalScrollbar] 相同。 */
internal object HorizontalScrollbar {
    /**
     * 繪製水平捲軸的軌道與滑塊。
     *
     * @param context 畫面繪製情境。
     * @param left 軌道左界。
     * @param right 軌道右界。
     * @param top 軌道上界。
     * @param height 軌道高度。
     * @param thumb 滑塊的左右邊界。
     */
    fun render(context: GuiGraphics, left: Int, right: Int, top: Int, height: Int, thumb: HorizontalScrollThumb) {
        context.fill(left, top, right, top + height, VerticalScrollbar.TRACK_COLOR)
        context.fill(thumb.left, top, thumb.right, top + height, VerticalScrollbar.THUMB_COLOR)
    }
}
