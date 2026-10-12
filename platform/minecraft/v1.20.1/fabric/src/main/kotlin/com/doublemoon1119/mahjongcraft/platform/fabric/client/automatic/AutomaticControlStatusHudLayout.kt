package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongHudBounds
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.hudCoordinate
import kotlin.math.ceil

/** 一列在未縮放面板內的位置；`rowIndex == null` 表示剩餘項目數。 */
internal data class AutomaticControlStatusPlacement(
    /** 對應的可見狀態列索引；摘要列為 `null`。 */
    val rowIndex: Int?,
    /** 未縮放面板中的 X 座標。 */
    val x: Int,
    /** 未縮放面板中的 Y 座標。 */
    val y: Int,
)

/** 完整狀態面板的 GUI scaled bounds 與局部繪製座標。 */
internal data class AutomaticControlStatusHudLayout(
    /** 縮放後並限制在螢幕內的面板範圍。 */
    val bounds: MahjongHudBounds,
    /** 縮放前的面板寬度。 */
    val rawWidth: Int,
    /** 縮放前的面板高度。 */
    val rawHeight: Int,
    /** 符合螢幕可用範圍的繪製縮放值。 */
    val scale: Float,
    /** 各可見列與摘要列的未縮放座標。 */
    val placements: List<AutomaticControlStatusPlacement>,
    /** 未顯示的狀態列數量。 */
    val hiddenCount: Int,
)

/**
 * 依包含陰影的實際文字寬高排列狀態列，空間不足時最多續成兩欄。
 *
 * @param rowWidths 每列含圓點、間距、文字及陰影的寬度。
 * @param textHeight 一列文字含陰影的高度。
 * @param screenWidth 畫面寬度。
 * @param screenHeight 畫面高度。
 * @param ratioX 面板水平錨點比例。
 * @param ratioY 面板垂直錨點比例。
 * @param summaryWidth 依隱藏項目數量計算摘要列寬度的函式。
 * @return 面板佈局；沒有列或畫面尺寸無效時為 null。
 */
internal fun automaticControlStatusHudLayout(
    rowWidths: List<Int>,
    textHeight: Int,
    screenWidth: Int,
    screenHeight: Int,
    ratioX: Double,
    ratioY: Double,
    summaryWidth: (Int) -> Int,
): AutomaticControlStatusHudLayout? {
    if (rowWidths.isEmpty() || textHeight <= 0 || screenWidth <= 0 || screenHeight <= 0) return null
    val rowsPerColumn = ((screenHeight - 2 * SCREEN_MARGIN - 2 * PADDING - textHeight) / ROW_HEIGHT + 1).coerceAtLeast(1)
    val twoColumnWidth = 2 * PADDING + 2 * rowWidths.max() + COLUMN_GAP
    val columns = if (rowWidths.size > rowsPerColumn && twoColumnWidth <= screenWidth - 2 * SCREEN_MARGIN) 2 else 1
    val capacity = rowsPerColumn * columns
    val visibleCount = if (rowWidths.size > capacity) (capacity - 1).coerceAtLeast(0) else rowWidths.size
    val hiddenCount = rowWidths.size - visibleCount
    val usedCount = visibleCount + if (hiddenCount > 0) 1 else 0
    val widths = IntArray(columns)
    repeat(visibleCount) { index ->
        widths[index / rowsPerColumn] = maxOf(widths[index / rowsPerColumn], rowWidths[index])
    }
    if (hiddenCount > 0) {
        val summaryColumn = visibleCount / rowsPerColumn
        widths[summaryColumn] = maxOf(widths[summaryColumn], summaryWidth(hiddenCount))
    }
    val rawWidth = 2 * PADDING + widths.sum() + (columns - 1) * COLUMN_GAP
    val rawHeight = 2 * PADDING + (minOf(usedCount, rowsPerColumn) - 1) * ROW_HEIGHT + textHeight
    val scale = minOf(
        1.0,
        (screenWidth - 2 * SCREEN_MARGIN).coerceAtLeast(1).toDouble() / rawWidth,
        (screenHeight - 2 * SCREEN_MARGIN).coerceAtLeast(1).toDouble() / rawHeight,
    ).toFloat()
    val scaledWidth = ceil(rawWidth * scale).toInt().coerceAtMost(screenWidth)
    val scaledHeight = ceil(rawHeight * scale).toInt().coerceAtMost(screenHeight)
    val columnStarts = IntArray(columns)
    for (column in 1 until columns) columnStarts[column] = columnStarts[column - 1] + widths[column - 1] + COLUMN_GAP
    val placements = (0 until usedCount).map { index ->
        val column = index / rowsPerColumn
        AutomaticControlStatusPlacement(
            rowIndex = index.takeIf { it < visibleCount },
            x = PADDING + columnStarts[column],
            y = PADDING + index % rowsPerColumn * ROW_HEIGHT,
        )
    }
    return AutomaticControlStatusHudLayout(
        bounds = MahjongHudBounds(
            left = hudCoordinate(ratioX, screenWidth, scaledWidth),
            top = hudCoordinate(ratioY, screenHeight, scaledHeight),
            width = scaledWidth,
            height = scaledHeight,
        ),
        rawWidth = rawWidth,
        rawHeight = rawHeight,
        scale = scale,
        placements = placements,
        hiddenCount = hiddenCount,
    )
}

/** 自動操作狀態面板中相鄰文字列的垂直步距（像素）。 */
internal const val AUTOMATIC_STATUS_ROW_HEIGHT = 12

/** 佈局計算使用的列高別名。 */
private const val ROW_HEIGHT = AUTOMATIC_STATUS_ROW_HEIGHT

/** 面板內容與邊緣之間的留白（像素）。 */
private const val PADDING = 6

/** 兩欄面板中欄位間的水平間隔（像素）。 */
private const val COLUMN_GAP = 10

/** 面板與螢幕邊緣之間的最小留白（像素）。 */
private const val SCREEN_MARGIN = 4
