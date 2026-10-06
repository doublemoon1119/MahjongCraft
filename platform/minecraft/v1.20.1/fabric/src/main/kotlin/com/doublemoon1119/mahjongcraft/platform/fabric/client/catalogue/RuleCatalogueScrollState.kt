package com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue

/**
 * 目錄內容區的像素捲動與捲軸拖曳狀態。
 *
 * 拖曳開始後即使游標離開捲軸也持續跟隨，直到放開左鍵；所有偏移都限制在可捲動範圍內。
 */
internal class RuleCatalogueScrollState {
    /** 目前內容的像素偏移。 */
    var offset: Double = 0.0
        private set

    /** 是否正在拖曳捲軸。 */
    var isDragging: Boolean = false
        private set

    /** 開始拖曳時游標相對於滑塊上緣的距離。 */
    private var grabOffset = 0.0

    /**
     * 以滾輪移動內容。
     *
     * @param amount 滾輪方向與幅度，向上為正。
     * @param layout 目前的畫面幾何。
     * @param contentHeight 內容總高度。
     */
    fun scrollBy(
        amount: Double,
        layout: RuleCatalogueScreenLayout,
        contentHeight: Int,
    ) {
        offset = layout.clampScroll(scroll = offset - amount * SCROLL_STEP, contentHeight = contentHeight)
    }

    /**
     * 在捲軸上按下左鍵：按在滑塊上時保留抓取位置，按在軌道上時把滑塊中心移到游標處。
     *
     * @param mouseY 游標垂直位置。
     * @param layout 目前的畫面幾何。
     * @param contentHeight 內容總高度。
     * @return 內容可捲動且開始拖曳時為 true。
     */
    fun press(
        mouseY: Double,
        layout: RuleCatalogueScreenLayout,
        contentHeight: Int,
    ): Boolean {
        val bar = layout.scrollbar(contentHeight = contentHeight, scroll = offset)
        if (bar.maximumScroll == 0) return false
        isDragging = true
        grabOffset = bar.grabOffset(mouseY)
        offset = bar.scrollIndexFor(mouseY, grabOffset).toDouble()
        return true
    }

    /**
     * 拖曳中依游標位置更新偏移。
     *
     * @param mouseY 游標垂直位置。
     * @param layout 目前的畫面幾何。
     * @param contentHeight 內容總高度。
     * @return 正在拖曳時為 true。
     */
    fun drag(
        mouseY: Double,
        layout: RuleCatalogueScreenLayout,
        contentHeight: Int,
    ): Boolean {
        if (!isDragging) return false
        offset = layout.scrollbar(contentHeight = contentHeight, scroll = offset).scrollIndexFor(mouseY, grabOffset).toDouble()
        return true
    }

    /**
     * 放開左鍵並結束拖曳。
     *
     * @return 原本正在拖曳時為 true。
     */
    fun release(): Boolean {
        val wasDragging = isDragging
        isDragging = false
        return wasDragging
    }

    /** 回到頂端並結束拖曳，用於內容條件改變。 */
    fun reset() {
        offset = 0.0
        isDragging = false
    }

    /**
     * 保留偏移但限制在新的可捲動範圍內並結束拖曳，用於視窗尺寸或語言改變。
     *
     * @param layout 新的畫面幾何。
     * @param contentHeight 新的內容總高度。
     */
    fun relayout(layout: RuleCatalogueScreenLayout, contentHeight: Int) {
        offset = layout.clampScroll(scroll = offset, contentHeight = contentHeight)
        isDragging = false
    }

    /** 捲動距離設定。 */
    internal companion object {
        /** 每格滾輪移動的像素。 */
        const val SCROLL_STEP: Int = 24
    }
}
