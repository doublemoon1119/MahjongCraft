package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * 判斷桌面物件是否會占用桌面中央區域。
 *
 * @param tableLocation 麻將桌位置。
 * @param target 物件的世界中心位置。
 * @param dimensions 物件的占用尺寸。
 * @return 物件的水平占用範圍與中央區域相交時為 `true`。
 */
fun intersectsTablePropCenter(
    tableLocation: TableLocation,
    target: TablePropTarget,
    dimensions: TablePropDimensions,
): Boolean {
    val centerX = tableLocation.x + 0.5
    val centerZ = tableLocation.z + 0.5
    val half = TablePropPresentation.CENTER_HALF_EXTENT
    val bounds = TablePropBounds.of(target, dimensions)
    return bounds.maxX > centerX - half &&
        bounds.minX < centerX + half &&
        bounds.maxZ > centerZ - half &&
        bounds.minZ < centerZ + half
}
