package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * 每個座位上可以擺放桌面物件的固定位置。
 *
 * 通用 layout 只知道這些位置在哪，不知道上面會放什麼；要放什麼、放幾個由規則的描述決定。
 */
enum class TableSeatAnchor {
    /** 這個座位牌河靠桌子中心那條邊的中點。 */
    DISCARD_INNER_EDGE,

    /** 這個座位的副露角落。 */
    MELD_CORNER,
}
