package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

/** 常駐桌級 overlay 的集中搜尋半徑；最後仍須以 managedTableId 精確篩選。 */
object TableOverlayEntitySearch {
    /** 桌級 overlay 查詢的水平半徑（方塊）。 */
    const val HORIZONTAL_RADIUS = 4.0

    /** 桌級 overlay 查詢的垂直半徑（方塊）。 */
    const val VERTICAL_RADIUS = 4.0
}
