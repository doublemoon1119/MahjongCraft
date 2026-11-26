package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * 所有麻將桌共用的固定範圍：控制方塊所在的一層，以及其上方擺放牌、牌牆、骰子與桌面物件的一層，
 * 兩層都是以控制方塊為中心的 3×3。範圍以控制方塊為中心對稱，桌子朝向不影響涵蓋的方塊。
 */
object MahjongTableSpace {
    /** 控制方塊到範圍水平邊緣的格數。 */
    const val HALF_WIDTH: Int = 1

    /** 擺牌那一層相對控制方塊的高度。 */
    const val PLAY_SPACE_OFFSET_Y: Int = 1

    /** 擺牌那一層各格相對控制方塊的水平位移，依 x 再依 z 排序。 */
    val playSpaceHorizontalOffsets: List<Pair<Int, Int>> = (-HALF_WIDTH..HALF_WIDTH).flatMap { dx ->
        (-HALF_WIDTH..HALF_WIDTH).map { dz -> dx to dz }
    }
}
