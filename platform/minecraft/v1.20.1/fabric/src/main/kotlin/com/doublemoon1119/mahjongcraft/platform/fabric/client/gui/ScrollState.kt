package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

/**
 * 可捲動清單目前的捲動位置與 scrollbar 拖曳狀態；統一滾輪、拖曳與版面重建收斂共用的行為，
 * 讓多個畫面的清單捲動維持一致的邊界與抓取點語意。
 */
internal class ScrollState {
    /** 目前第一個可見項目的 index。 */
    var index: Int = 0
        private set

    /** 是否正在拖曳 scrollbar thumb。 */
    var dragging: Boolean = false
        private set

    /** 拖曳中，游標在 thumb 內的抓取偏移。 */
    private var grabOffset: Double = 0.0

    /** 版面重建或項目總數變動時，把目前 index 收斂回合法範圍。 */
    fun clamp(maximumScroll: Int) {
        index = index.coerceIn(0, maximumScroll.coerceAtLeast(0))
    }

    /** 依滾輪方向與目前合法上限更新 index；回傳是否真的改變了 index。 */
    fun scrollBy(amount: Double, maximumScroll: Int): Boolean {
        if (amount == 0.0 || maximumScroll <= 0) return false
        val next = (index + if (amount < 0) 1 else -1).coerceIn(0, maximumScroll)
        if (next == index) return false
        index = next
        return true
    }

    /**
     * 開始拖曳；記錄游標在 thumb 內的抓取偏移，避免跳到以游標為 thumb 上界的位置。回傳是否真的
     * 改變了 index，供呼叫端判斷是否需要重建 widgets。
     */
    fun beginDrag(mouseY: Double, layout: ScrollbarLayout): Boolean {
        dragging = true
        grabOffset = layout.grabOffset(mouseY)
        return dragTo(mouseY, layout)
    }

    /** 拖曳中依游標位置換算並更新 index；回傳是否真的改變了 index。 */
    fun dragTo(mouseY: Double, layout: ScrollbarLayout): Boolean {
        val next = layout.scrollIndexFor(mouseY, grabOffset)
        if (next == index) return false
        index = next
        return true
    }

    /** 結束拖曳。 */
    fun endDrag() {
        dragging = false
    }

    /** 直接重置至頂端，例如切換分類時。 */
    fun reset() {
        index = 0
    }
}
