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

    /** 進入下一層畫面：向右的實心三角形。 */
    OPEN_RIGHT(
        width = 4,
        height = 7,
        rows = listOf(
            listOf(0 to 1),
            listOf(0 to 2),
            listOf(0 to 3),
            listOf(0 to 4),
            listOf(0 to 3),
            listOf(0 to 2),
            listOf(0 to 1),
        ),
    ),

    /** 上一個：向左的實心三角形，與 [OPEN_RIGHT] 左右對稱。 */
    PREVIOUS_LEFT(
        width = 4,
        height = 7,
        rows = listOf(
            listOf(3 to 1),
            listOf(2 to 2),
            listOf(1 to 3),
            listOf(0 to 4),
            listOf(1 to 3),
            listOf(2 to 2),
            listOf(3 to 1),
        ),
    ),

    /** 已勾選：與下拉清單目前選項相同的打勾。 */
    CHECK(
        width = PixelIcons.CHECK_WIDTH,
        height = PixelIcons.CHECK_HEIGHT,
        rows = listOf(
            listOf(6 to 1),
            listOf(5 to 2),
            listOf(0 to 1, 4 to 2),
            listOf(0 to 2, 3 to 2),
            listOf(1 to 3),
            listOf(2 to 1),
        ),
    ),

    /** 移除：粗體叉號。 */
    CROSS(
        width = 7,
        height = 7,
        rows = listOf(
            listOf(0 to 2, 5 to 2),
            listOf(0 to 3, 4 to 3),
            listOf(1 to 5),
            listOf(2 to 3),
            listOf(1 to 5),
            listOf(0 to 3, 4 to 3),
            listOf(0 to 2, 5 to 2),
        ),
    ),

    /** 查看詳細內容：放大鏡。 */
    MAGNIFIER(
        width = 9,
        height = 9,
        rows = listOf(
            listOf(1 to 4),
            listOf(0 to 1, 5 to 1),
            listOf(0 to 1, 5 to 1),
            listOf(0 to 1, 5 to 1),
            listOf(0 to 1, 5 to 1),
            listOf(1 to 4, 6 to 1),
            listOf(6 to 2),
            listOf(7 to 2),
            listOf(8 to 1),
        ),
    ),

    /** 跳到第一個：左側直條加向左的三角形。 */
    SKIP_FIRST(
        width = 7,
        height = 7,
        rows = listOf(
            listOf(0 to 2, 6 to 1),
            listOf(0 to 2, 5 to 2),
            listOf(0 to 2, 4 to 3),
            listOf(0 to 2, 3 to 4),
            listOf(0 to 2, 4 to 3),
            listOf(0 to 2, 5 to 2),
            listOf(0 to 2, 6 to 1),
        ),
    ),

    /** 往前一個：兩個向左的三角形。 */
    STEP_BACKWARD(
        width = 8,
        height = 7,
        rows = listOf(
            listOf(3 to 1, 7 to 1),
            listOf(2 to 2, 6 to 2),
            listOf(1 to 3, 5 to 3),
            listOf(0 to 8),
            listOf(1 to 3, 5 to 3),
            listOf(2 to 2, 6 to 2),
            listOf(3 to 1, 7 to 1),
        ),
    ),

    /** 播放：向右的實心三角形。 */
    PLAY(
        width = 4,
        height = 7,
        rows = listOf(
            listOf(0 to 1),
            listOf(0 to 2),
            listOf(0 to 3),
            listOf(0 to 4),
            listOf(0 to 3),
            listOf(0 to 2),
            listOf(0 to 1),
        ),
    ),

    /** 暫停：兩條直條。 */
    PAUSE(
        width = 5,
        height = 7,
        rows = List(size = 7) { listOf(0 to 2, 3 to 2) },
    ),

    /** 往後一個：兩個向右的三角形，與 [STEP_BACKWARD] 左右對稱。 */
    STEP_FORWARD(
        width = 8,
        height = 7,
        rows = listOf(
            listOf(0 to 1, 4 to 1),
            listOf(0 to 2, 4 to 2),
            listOf(0 to 3, 4 to 3),
            listOf(0 to 8),
            listOf(0 to 3, 4 to 3),
            listOf(0 to 2, 4 to 2),
            listOf(0 to 1, 4 to 1),
        ),
    ),

    /** 進入全螢幕：四個朝外的角。 */
    FULLSCREEN_ENTER(
        width = 7,
        height = 7,
        rows = listOf(
            listOf(0 to 3, 4 to 3),
            listOf(0 to 1, 6 to 1),
            listOf(0 to 1, 6 to 1),
            emptyList(),
            listOf(0 to 1, 6 to 1),
            listOf(0 to 1, 6 to 1),
            listOf(0 to 3, 4 to 3),
        ),
    ),

    /** 結束全螢幕：四個朝內的角，與 [FULLSCREEN_ENTER] 相反。 */
    FULLSCREEN_EXIT(
        width = 7,
        height = 7,
        rows = listOf(
            listOf(2 to 1, 4 to 1),
            listOf(2 to 1, 4 to 1),
            listOf(0 to 3, 4 to 3),
            emptyList(),
            listOf(0 to 3, 4 to 3),
            listOf(2 to 1, 4 to 1),
            listOf(2 to 1, 4 to 1),
        ),
    ),

    /** 跳到最後一個：向右的三角形加右側直條，與 [SKIP_FIRST] 左右對稱。 */
    SKIP_LAST(
        width = 7,
        height = 7,
        rows = listOf(
            listOf(0 to 1, 5 to 2),
            listOf(0 to 2, 5 to 2),
            listOf(0 to 3, 5 to 2),
            listOf(0 to 4, 5 to 2),
            listOf(0 to 3, 5 to 2),
            listOf(0 to 2, 5 to 2),
            listOf(0 to 1, 5 to 2),
        ),
    ),
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
