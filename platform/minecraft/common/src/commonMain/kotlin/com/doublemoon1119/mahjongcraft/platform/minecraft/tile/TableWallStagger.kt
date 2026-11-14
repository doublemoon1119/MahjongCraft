package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 四面牌牆錯開的方向，以從桌外面向一面牌牆的人來看。
 *
 * 每面牌牆都往同一個方向錯開，轉角處由一面牌牆伸出、相鄰牌牆的端點頂在它的側邊。各地區與各家習慣不同，例如日本麻將
 * 往右斜前方推出牌山。
 */
enum class TableWallStagger {
    /** 每面牌牆往面向它的人的右手邊錯開，自己右手邊的轉角由自己的牌牆伸出。 */
    RIGHT,

    /** 每面牌牆往面向它的人的左手邊錯開，自己左手邊的轉角由自己的牌牆伸出。 */
    LEFT,
}
