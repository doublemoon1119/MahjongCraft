package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

/** 歷史篩選頁固定標題／頁尾與可捲動內容區的版面計算。
 *
 * @property headerBottom 標題區下界。
 * @property footerTop 頁尾按鈕上界。
 * @property contentTop 內容區上界。
 * @property contentBottom 內容區下界。
 * @property rowSpacing 控制項列間距。
 * @property rowHeight 控制項高度。
 * @property scrollableHeight 內容超出可視區時可捲動的總高度。
 */
internal data class HistoryFilterLayout(
    val headerBottom: Int,
    val footerTop: Int,
    val contentTop: Int,
    val contentBottom: Int,
    val rowSpacing: Int,
    val rowHeight: Int,
    val scrollableHeight: Int,
) {
    /** 取得內容列的垂直座標。
     *
     * @param rowIndex 從零開始的列索引。
     * @param scroll 內容區目前的捲動距離。
     * @return 未裁切的控制項上界。
     */
    fun rowTop(rowIndex: Int, scroll: Int): Int = contentTop + rowIndex * rowSpacing - scroll

    /** 判斷完整控制項是否位於內容區內，避免覆蓋標題與頁尾。
     *
     * @param rowIndex 從零開始的列索引。
     * @param scroll 內容區目前的捲動距離。
     * @return 是否需要顯示該列。
     */
    fun isVisible(rowIndex: Int, scroll: Int): Boolean {
        val top = rowTop(rowIndex, scroll)
        return top >= contentTop && top + rowHeight <= contentBottom
    }

    /** 篩選內容的幾何建立入口。 */
    companion object {
        /**
         * 建立固定標題、頁尾與可捲動列的版面。
         *
         * @param height 畫面高度。
         * @param rowCount 內容列數。
         * @return 含捲動上限的版面資料。
         */
        fun create(height: Int, rowCount: Int): HistoryFilterLayout {
            val headerBottom = 30
            val footerTop = (height - 28).coerceAtLeast(headerBottom + 1)
            val contentTop = headerBottom + 4
            val contentBottom = (footerTop - 6).coerceAtLeast(contentTop + 1)
            val rowHeight = if (height < 230) 16 else 18
            val rowSpacing = rowHeight + 2
            val total = (rowCount * rowSpacing).coerceAtLeast(0)
            return HistoryFilterLayout(headerBottom, footerTop, contentTop, contentBottom, rowSpacing, rowHeight, (total - (contentBottom - contentTop)).coerceAtLeast(0))
        }
    }
}
