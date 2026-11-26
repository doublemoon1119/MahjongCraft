package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * 描述桌面物件在桌面上的實際占用範圍。
 *
 * @property width 水平 X 軸方向的寬度。
 * @property height 垂直方向的高度。
 * @property depth 水平 Z 軸方向的深度。
 */
data class TablePropDimensions(
    val width: Double,
    val height: Double,
    val depth: Double,
) {
    init {
        require(width.isFinite() && width > 0.0) { "Table prop width must be positive: $width" }
        require(height.isFinite() && height > 0.0) { "Table prop height must be positive: $height" }
        require(depth.isFinite() && depth > 0.0) { "Table prop depth must be positive: $depth" }
    }
}
