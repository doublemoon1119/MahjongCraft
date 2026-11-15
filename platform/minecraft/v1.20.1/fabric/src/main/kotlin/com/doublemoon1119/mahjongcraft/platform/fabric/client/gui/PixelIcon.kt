package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

/**
 * 只有圖示的按鈕上使用的像素圖示，由 [PixelIcons.icon] 繪製。
 *
 * @property width 圖示寬度。
 * @property height 圖示高度。
 * @property rows 每一列的像素範圍：起點與長度，由上到下；一列可以有多段。
 */
internal enum class PixelIcon(
    val width: Int,
    val height: Int,
    val rows: List<List<Pair<Int, Int>>>,
) {
    /** 由小到大排序：向上的箭頭。 */
    SORT_ASCENDING(width = 6, height = 7, rows = SortArrowRows.DOWN.reversed()),

    /** 由大到小排序：向下的箭頭。 */
    SORT_DESCENDING(width = 6, height = 7, rows = SortArrowRows.DOWN),
}

/** 排序箭頭的像素範圍。 */
private object SortArrowRows {
    /** 向下箭頭：四列箭身接三列箭頭。 */
    val DOWN: List<List<Pair<Int, Int>>> = listOf(
        listOf(2 to 2),
        listOf(2 to 2),
        listOf(2 to 2),
        listOf(2 to 2),
        listOf(0 to 6),
        listOf(1 to 4),
        listOf(2 to 2),
    )
}
