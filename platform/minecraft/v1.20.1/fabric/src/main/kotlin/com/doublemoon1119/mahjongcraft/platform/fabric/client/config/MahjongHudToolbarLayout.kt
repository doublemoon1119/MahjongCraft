package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.HorizontalScrollLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.HorizontalScrollThumb

/**
 * HUD 位置編輯器頂部工具列的版位計算，與原版 widget 完全分離。
 *
 * 第一行固定放 HUD、其他 HUD 預覽方式、隱藏控制項三顆全域按鈕，版位直接固定、不需要這個類別。這個
 * 類別只負責第二行——目前編輯的 HUD 有二級選項（例如操作面板的情境）時才出現的那一行——的水平捲動
 * 幾何；選項數量不多時不需要捲動，但保留這個機制讓未來規則模組能安全地為自己的 HUD 元件登記更多
 * 二級選項，不會因為選項變多就擠出畫面外。
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
     * 依捲動量計算這一行內容的實際起始 X；一律靠左對齊，未溢出時 [scroll] 必為 0，公式自然回到
     * [MARGIN] 本身，不需要另外處理置中。
     */
    fun contentOffset(scroll: Double): Int = MARGIN - scroll.toInt()

    /** 依可見比例與目前捲動量計算 scrollbar thumb 邊界。 */
    fun thumb(scroll: Double): HorizontalScrollThumb = this.scroll.thumb(scroll)

    /** 將 thumb 左界轉換回捲動量，供直接點擊或拖曳 scrollbar 使用。 */
    fun scrollFromThumb(thumbLeft: Double): Double = scroll.scrollFromThumbLeft(thumbLeft)

    /** 依 thumb 拖曳位移計算新的捲動量，並限制在合法範圍內。 */
    fun scrollFromDrag(startScroll: Double, pointerDelta: Double): Double = scroll.scrollFromDrag(startScroll, pointerDelta)

    /** 依滾輪量計算新的捲動量，並限制在合法範圍內。 */
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
