package com.doublemoon1119.mahjongcraft.platform.fabric.block

import net.minecraft.util.StringIdentifiable

/**
 * 標示 3×3×2 麻將桌中的固定相對位置。
 *
 * [localX] 與 [localZ] 以朝北的 controller 為基準，[localY] 為相對 controller 的垂直位移。
 * `BOTTOM_CENTER` 是唯一允許建立方塊實體及保存權威桌子識別碼的 part。
 *
 * @property localX 朝北時相對 controller 的 X 位移。
 * @property localY 相對 controller 的 Y 位移。
 * @property localZ 朝北時相對 controller 的 Z 位移。
 * @property serializedName blockstate 使用的穩定名稱。
 */
enum class MahjongTablePart(
    val localX: Int,
    val localY: Int,
    val localZ: Int,
    private val serializedName: String,
) : StringIdentifiable {
    /** 底層西北角。 */
    BOTTOM_NORTH_WEST(-1, 0, -1, "bottom_north_west"),

    /** 底層北側中央。 */
    BOTTOM_NORTH(0, 0, -1, "bottom_north"),

    /** 底層東北角。 */
    BOTTOM_NORTH_EAST(1, 0, -1, "bottom_north_east"),

    /** 底層西側中央。 */
    BOTTOM_WEST(-1, 0, 0, "bottom_west"),

    /** 底層 controller。 */
    BOTTOM_CENTER(0, 0, 0, "bottom_center"),

    /** 底層東側中央。 */
    BOTTOM_EAST(1, 0, 0, "bottom_east"),

    /** 底層西南角。 */
    BOTTOM_SOUTH_WEST(-1, 0, 1, "bottom_south_west"),

    /** 底層南側中央。 */
    BOTTOM_SOUTH(0, 0, 1, "bottom_south"),

    /** 底層東南角。 */
    BOTTOM_SOUTH_EAST(1, 0, 1, "bottom_south_east"),

    /** 上層西北角。 */
    TOP_NORTH_WEST(-1, 1, -1, "top_north_west"),

    /** 上層北側中央。 */
    TOP_NORTH(0, 1, -1, "top_north"),

    /** 上層東北角。 */
    TOP_NORTH_EAST(1, 1, -1, "top_north_east"),

    /** 上層西側中央。 */
    TOP_WEST(-1, 1, 0, "top_west"),

    /** 上層中央保留空間。 */
    TOP_CENTER(0, 1, 0, "top_center"),

    /** 上層東側中央。 */
    TOP_EAST(1, 1, 0, "top_east"),

    /** 上層西南角。 */
    TOP_SOUTH_WEST(-1, 1, 1, "top_south_west"),

    /** 上層南側中央。 */
    TOP_SOUTH(0, 1, 1, "top_south"),

    /** 上層東南角。 */
    TOP_SOUTH_EAST(1, 1, 1, "top_south_east"),
    ;

    /** 回傳 blockstate serialization 使用的名稱。 */
    override fun asString(): String = serializedName
}
