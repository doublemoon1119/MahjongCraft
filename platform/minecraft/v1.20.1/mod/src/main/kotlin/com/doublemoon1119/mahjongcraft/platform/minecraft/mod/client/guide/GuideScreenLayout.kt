package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ButtonRowLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.PixelScrollViewport
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScrollbarLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.TabStripLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar

/**
 * 「如何開始」畫面的版面：標題、三個頁籤、可捲動的內容區與底部按鈕列，左右邊界與設定類畫面相同。
 *
 * @property frame 與設定類畫面共用的外框版面。
 */
internal data class GuideScreenLayout(val frame: FormScreenLayout) : PixelScrollViewport {
    /** 內容區左界。 */
    val contentLeft: Int get() = frame.contentLeft

    /** 內容區上界。 */
    val contentTop: Int get() = frame.fieldsTop

    /** 內容區寬度。 */
    val contentWidth: Int get() = frame.contentWidth

    /** 內容區高度。 */
    val contentHeight: Int get() = (frame.fieldsBottom - frame.fieldsTop).coerceAtLeast(0)

    /** 內容區下界（不含）。 */
    val contentBottom: Int get() = contentTop + contentHeight

    /** 捲軸左界。 */
    val scrollbarLeft: Int get() = frame.scrollbarLeft

    /** 底部按鈕列的上界。 */
    val footerY: Int get() = frame.footerY

    /** 底部按鈕列。 */
    val footer: ButtonRowLayout get() = frame.footer(FOOTER_SLOTS)

    /** 頁籤列。 */
    val tabs: TabStripLayout get() = TabStripLayout(left = contentLeft, width = contentWidth, top = frame.tabsTop, tabCount = GuideTab.entries.size)

    /**
     * 判斷位置是否在內容區內。
     *
     * @param x 水平位置。
     * @param y 垂直位置。
     * @return 在內容區內時為 true。
     */
    fun inContent(x: Double, y: Double): Boolean = x >= contentLeft && x < contentLeft + contentWidth && y >= contentTop && y < contentBottom

    /**
     * 判斷位置是否在捲軸的命中範圍內。
     *
     * @param x 水平位置。
     * @param y 垂直位置。
     * @return 在捲軸命中範圍內時為 true。
     */
    fun onScrollbar(x: Double, y: Double): Boolean = frame.isOverScrollbar(x, y)

    /**
     * 依內容總高度與目前像素偏移建立垂直捲軸。
     *
     * @param contentHeight 內容總高度，單位為像素。
     * @param scroll 目前內容偏移，單位為像素。
     * @return 以像素為單位的捲軸幾何。
     */
    override fun scrollbar(contentHeight: Int, scroll: Double): ScrollbarLayout {
        val viewport = this.contentHeight.coerceAtLeast(1)
        return ScrollbarLayout(
            trackTop = contentTop,
            trackBottom = contentBottom,
            itemCount = contentHeight.coerceAtLeast(viewport),
            visibleItemCount = viewport,
            scrollIndex = clampScroll(scroll = scroll, contentHeight = contentHeight).toInt(),
            minimumThumbHeight = VerticalScrollbar.MINIMUM_THUMB_HEIGHT.coerceAtMost(viewport),
        )
    }

    /**
     * 將內容偏移限制在可捲動範圍內。
     *
     * @param scroll 目前內容偏移，單位為像素。
     * @param contentHeight 內容總高度，單位為像素。
     * @return 限制後的內容偏移。
     */
    override fun clampScroll(scroll: Double, contentHeight: Int): Double = scroll.coerceIn(0.0, (contentHeight - this.contentHeight).coerceAtLeast(0).toDouble())

    /** 版面的建立入口與尺寸。 */
    internal companion object {
        /** 底部按鈕列的格數；返回或關閉固定在最後一格。 */
        const val FOOTER_SLOTS: Int = 4

        /** 返回或關閉按鈕在底部按鈕列的位置。 */
        const val CLOSE_SLOT: Int = FOOTER_SLOTS - 1

        /**
         * 依視窗尺寸建立版面。
         *
         * @param width 視窗寬度。
         * @param height 視窗高度。
         * @return 版面。
         */
        fun of(width: Int, height: Int): GuideScreenLayout = GuideScreenLayout(
            frame = FormScreenLayout(
                windowWidth = width.coerceAtLeast(1),
                windowHeight = height.coerceAtLeast(1),
                hasPageSwitch = false,
                hasRuleRow = false,
            ),
        )
    }
}
