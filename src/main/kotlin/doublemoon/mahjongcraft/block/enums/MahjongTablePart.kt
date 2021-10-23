package doublemoon.mahjongcraft.block.enums

import net.minecraft.util.StringIdentifiable

enum class MahjongTablePart : StringIdentifiable {
    //中心
    BOTTOM_CENTER,

    //以下都是外圍
    BOTTOM_EAST,
    BOTTOM_WEST,
    BOTTOM_SOUTH,
    BOTTOM_NORTH,
    BOTTOM_SOUTHEAST,
    BOTTOM_SOUTHWEST,
    BOTTOM_NORTHEAST,
    BOTTOM_NORTHWEST,
    TOP_CENTER,
    TOP_EAST,
    TOP_WEST,
    TOP_SOUTH,
    TOP_NORTH,
    TOP_SOUTHEAST,
    TOP_SOUTHWEST,
    TOP_NORTHEAST,
    TOP_NORTHWEST,
    ;


    //property must be lowercase
    override fun toString(): String = this.name.lowercase()

    override fun asString(): String = this.name.lowercase()
}