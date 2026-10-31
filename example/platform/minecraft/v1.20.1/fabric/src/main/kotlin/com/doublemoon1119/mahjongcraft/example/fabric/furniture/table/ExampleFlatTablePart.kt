package com.doublemoon1119.mahjongcraft.example.fabric.furniture.table

import net.minecraft.util.StringIdentifiable

/**
 * 3×3 單層範例桌子中的一格，以世界方向表示相對中央的位置；桌子朝向只影響牌桌布局，不改變各格位置。
 *
 * @property offsetX 相對中央的 x 位移。
 * @property offsetZ 相對中央的 z 位移。
 */
enum class ExampleFlatTablePart(
    val offsetX: Int,
    val offsetZ: Int,
) : StringIdentifiable {
    /** 中央，也是桌子的控制方塊。 */
    CENTER(0, 0),

    /** 北邊。 */
    NORTH(0, -1),

    /** 東邊。 */
    EAST(1, 0),

    /** 南邊。 */
    SOUTH(0, 1),

    /** 西邊。 */
    WEST(-1, 0),

    /** 西北角。 */
    NORTH_WEST(-1, -1),

    /** 東北角。 */
    NORTH_EAST(1, -1),

    /** 東南角。 */
    SOUTH_EAST(1, 1),

    /** 西南角。 */
    SOUTH_WEST(-1, 1),
    ;

    /** @return 方塊狀態中使用的小寫名稱。 */
    override fun asString(): String = name.lowercase()
}
