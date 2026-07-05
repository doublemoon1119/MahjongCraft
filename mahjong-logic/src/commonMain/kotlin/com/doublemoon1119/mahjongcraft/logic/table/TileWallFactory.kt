package com.doublemoon1119.mahjongcraft.logic.table

/**
 * 牌山生成工廠介面。
 * 根據特定地區規則定義牌山的組成。
 */
interface TileWallFactory {
    /**
     * 建立一個全新的、已經洗牌的 [TileWall]。
     */
    fun create(): TileWall
}