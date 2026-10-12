package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.HorizontalScrollLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.HorizontalScrollThumb

/**
 * HUD 位置編輯器頂部工具列的版位計算，與原版 widget 完全分離。
 *
 * 只計算第二行選項的水平捲動幾何，例如操作面板的預覽情境。第一行三顆全域按鈕由畫面另外配置。
 * 第二行內容超出可見寬度時顯示水平捲軸。
 *
 * @property screenWidth 目前 GUI scaled 畫面寬度。
 * @property contentWidth 這一行全部按鈕與間距所需的內容寬度。
 */
internal data class MahjongHudToolbarLayout(
    val screenWidth: Int,
    val contentWidth: Int,
) {
    /** 可捲動 viewport 的可見寬度。 */
    val viewportWidth: Int
        get() = (screenWidth - MARGIN * 2).coerceAtLeast(1)

    /** 內容是否超出可見寬度，決定是否需要 scrollbar。 */
    val hasOverflow: Boolean
        get() = contentWidth > viewportWidth

    /** 這一行的水平捲動幾何。 */
    private val scroll: HorizontalScrollLayout
        get() = HorizontalScrollLayout(
            viewportLeft = MARGIN,
            viewportWidth = viewportWidth,
            contentWidth = contentWidth,
            minimumThumbWidth = MIN_THUMB_WIDTH,
        )

    /** 最大水平捲動量；沒有溢出時為零。 */
    val maximumScroll: Double
        get() = scroll.maximumScroll

    /**
     * 依捲動量計算內容起始 X；內容一律靠左對齊。
     *
     * @param scroll 呼叫端已限制在合法範圍內的捲動量；未溢出時應為零。
     * @return 扣除捲動量後的內容左界。
     */
    fun contentOffset(scroll: Double): Int = MARGIN - scroll.toInt()

    /**
     * 依可見比例與目前捲動量計算捲軸滑塊邊界。
     *
     * @param scroll 目前水平捲動量。
     * @return 捲軸滑塊的水平範圍。
     */
    fun thumb(scroll: Double): HorizontalScrollThumb = this.scroll.thumb(scroll)

    /**
     * 將滑塊左界轉換成捲動量。
     *
     * @param thumbLeft 滑塊左界座標。
     * @return 對應的合法內容捲動量。
     */
    fun scrollFromThumb(thumbLeft: Double): Double = scroll.scrollFromThumbLeft(thumbLeft)

    /**
     * 依滑塊拖曳位移計算新的合法捲動量。
     *
     * @param startScroll 拖曳開始時的內容捲動量。
     * @param pointerDelta 游標水平位移。
     * @return 限制在合法範圍內的新捲動量。
     */
    fun scrollFromDrag(startScroll: Double, pointerDelta: Double): Double = scroll.scrollFromDrag(startScroll, pointerDelta)

    /**
     * 依滾輪量計算新的合法捲動量。
     *
     * @param currentScroll 目前內容捲動量。
     * @param amount 滾輪位移量。
     * @return 限制在合法範圍內的新捲動量。
     */
    fun scrollFromWheel(currentScroll: Double, amount: Double): Double = scroll.scrollFromWheel(currentScroll, amount, SCROLL_STEP)

    /** 版位常數。 */
    internal companion object {
        /** 左右邊距。 */
        internal const val MARGIN = 12

        /** 第一行（HUD、其他 HUD 預覽、隱藏控制項）上界。 */
        internal const val TOP = 26

        /** 按鈕高度。 */
        internal const val BUTTON_HEIGHT = 20

        /** 按鈕間距。 */
        internal const val GAP = 4

        /** 兩行按鈕之間、以及按鈕行與 scrollbar 之間的間距。 */
        internal const val ROW_GAP = 4

        /** scrollbar 高度。 */
        internal const val SCROLLBAR_HEIGHT = 4

        /** scrollbar thumb 最小寬度。 */
        internal const val MIN_THUMB_WIDTH = 18

        /** 滾輪每格移動距離。 */
        internal const val SCROLL_STEP = 48.0

        /** 第二行情境選擇按鈕寬度；第一行三顆全域按鈕改為三等分整行寬度，不使用固定寬度。 */
        internal const val SELECTOR_WIDTH = 132
    }
}
