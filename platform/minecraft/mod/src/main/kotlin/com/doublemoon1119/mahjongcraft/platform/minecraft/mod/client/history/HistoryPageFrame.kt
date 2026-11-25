package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ButtonRowLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar

/**
 * 牌譜各頁共用的外框：內容寬度、底部四格與捲軸位置。
 *
 * 內容寬度最多 [FormScreenLayout.MAX_CONTENT_WIDTH] 並水平置中，左右邊距與設定類畫面相同；底部四格橫跨內容寬度，
 * 最右一格放離開畫面的按鈕；捲軸畫在內容右側的邊距內。
 *
 * @property windowWidth 目前視窗寬度。
 * @property windowHeight 目前視窗高度。
 */
internal data class HistoryPageFrame(
    val windowWidth: Int,
    val windowHeight: Int,
) {
    /** 內容寬度。 */
    val contentWidth: Int = (windowWidth - (if (windowWidth < FormScreenLayout.COMPACT_MIN_WIDTH) FormScreenLayout.COMPACT_MARGIN else FormScreenLayout.REGULAR_MARGIN) * 2)
        .coerceAtMost(FormScreenLayout.MAX_CONTENT_WIDTH)
        .coerceAtLeast(1)

    /** 內容左界。 */
    val contentLeft: Int = (windowWidth - contentWidth) / 2

    /** 內容右界。 */
    val contentRight: Int = contentLeft + contentWidth

    /** 底部按鈕列上界。 */
    val footerTop: Int = (windowHeight - FormScreenLayout.FOOTER_BOTTOM_MARGIN - FormScreenLayout.BUTTON_HEIGHT).coerceAtLeast(0)

    /** 捲軸左界。 */
    val scrollbarLeft: Int = contentRight + FormScreenLayout.SCROLLBAR_GAP

    /** 底部按鈕列：四格等寬，橫跨內容寬度。 */
    private val footer: ButtonRowLayout = ButtonRowLayout(
        left = contentLeft,
        width = contentWidth.coerceAtLeast(ButtonRowLayout.minimumWidth(FOOTER_SLOTS)),
        buttonCount = FOOTER_SLOTS,
    )

    /**
     * 取得底部按鈕列中一格的範圍。
     *
     * @param index 由左至右的順序，從 0 開始；最右一格是 [LAST_SLOT]。
     * @return 這一格的範圍。
     */
    fun footerSlot(index: Int): HistoryScreenLayout.Bounds = HistoryScreenLayout.Bounds(
        x = footer.buttonX(index),
        y = footerTop,
        width = footer.buttonWidth(index),
        height = FormScreenLayout.BUTTON_HEIGHT,
    )

    /**
     * 取得內容區右側捲軸的範圍。
     *
     * @param top 內容區上界。
     * @param bottom 內容區下界。
     * @return 捲軸軌道的範圍。
     */
    fun scrollbarBounds(top: Int, bottom: Int): HistoryScreenLayout.Bounds = HistoryScreenLayout.Bounds(
        x = scrollbarLeft,
        y = top,
        width = VerticalScrollbar.WIDTH,
        height = (bottom - top).coerceAtLeast(0),
    )

    /**
     * 判斷游標是否落在捲軸的命中範圍內；命中範圍比繪製範圍稍寬。
     *
     * @param mouseX 游標的水平座標。
     * @param mouseY 游標的垂直座標。
     * @param top 內容區上界。
     * @param bottom 內容區下界。
     * @return 位於命中範圍內時為 true。
     */
    fun isOverScrollbar(mouseX: Double, mouseY: Double, top: Int, bottom: Int): Boolean = mouseX >= scrollbarLeft - FormScreenLayout.SCROLLBAR_HIT_PADDING &&
        mouseX < scrollbarLeft + VerticalScrollbar.WIDTH + FormScreenLayout.SCROLLBAR_HIT_PADDING &&
        mouseY >= top &&
        mouseY < bottom

    internal companion object {
        /** 底部按鈕列的格數。 */
        const val FOOTER_SLOTS: Int = 4

        /** 最右一格的索引，放離開畫面的按鈕。 */
        const val LAST_SLOT: Int = FOOTER_SLOTS - 1

        /** 內容區與底部按鈕列之間的垂直間距。 */
        const val FOOTER_GAP: Int = 4
    }
}
