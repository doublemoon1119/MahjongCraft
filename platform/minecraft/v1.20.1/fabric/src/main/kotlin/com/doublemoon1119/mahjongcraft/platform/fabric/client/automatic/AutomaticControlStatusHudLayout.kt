package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongHudBounds
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.hudCoordinate
import kotlin.math.ceil

/** 一列在未縮放面板內的位置；`rowIndex == null` 表示剩餘項目數。 */
internal data class AutomaticControlStatusPlacement(
    val rowIndex: Int?,
    val x: Int,
    val y: Int,
)

/** 完整狀態面板的 GUI scaled bounds 與局部繪製座標。 */
internal data class AutomaticControlStatusHudLayout(
    val bounds: MahjongHudBounds,
    val rawWidth: Int,
    val rawHeight: Int,
    val scale: Float,
    val placements: List<AutomaticControlStatusPlacement>,
    val hiddenCount: Int,
)

/** 依包含陰影的實際文字寬高排列狀態列，空間不足時最多續成兩欄。 */
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

internal const val AUTOMATIC_STATUS_ROW_HEIGHT = 12
private const val ROW_HEIGHT = AUTOMATIC_STATUS_ROW_HEIGHT
private const val PADDING = 6
private const val COLUMN_GAP = 10
private const val SCREEN_MARGIN = 4
