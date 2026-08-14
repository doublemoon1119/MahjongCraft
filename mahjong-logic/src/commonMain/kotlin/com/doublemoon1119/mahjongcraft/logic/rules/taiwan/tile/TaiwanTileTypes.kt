package com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId

/** 台灣麻將內建八張花牌的穩定 ID 與既有規則判讀。 */
object TaiwanTileTypes {
    /** 春。 */
    val SPRING: TileTypeId = TileTypeId.parse("mahjongcraft:taiwan/spring")

    /** 夏。 */
    val SUMMER: TileTypeId = TileTypeId.parse("mahjongcraft:taiwan/summer")

    /** 秋。 */
    val AUTUMN: TileTypeId = TileTypeId.parse("mahjongcraft:taiwan/autumn")

    /** 冬。 */
    val WINTER: TileTypeId = TileTypeId.parse("mahjongcraft:taiwan/winter")

    /** 梅。 */
    val PLUM: TileTypeId = TileTypeId.parse("mahjongcraft:taiwan/plum")

    /** 蘭。 */
    val ORCHID: TileTypeId = TileTypeId.parse("mahjongcraft:taiwan/orchid")

    /** 竹。 */
    val BAMBOO: TileTypeId = TileTypeId.parse("mahjongcraft:taiwan/bamboo")

    /** 菊。 */
    val CHRYSANTHEMUM: TileTypeId = TileTypeId.parse("mahjongcraft:taiwan/chrysanthemum")

    /** 依春、夏、秋、冬、梅、蘭、竹、菊排列的全部內建花牌 ID。 */
    val ALL: List<TileTypeId> = listOf(SPRING, SUMMER, AUTUMN, WINTER, PLUM, ORCHID, BAMBOO, CHRYSANTHEMUM)

    /** 建立八張各一張的台灣麻將花牌。 */
    fun createAll(): List<Tile.Extension> = ALL.map(Tile::Extension)

    /** 判斷 [tile] 是否為台灣麻將內建花牌。 */
    fun isFlower(tile: Tile): Boolean = (tile as? Tile.Extension)?.typeId in ALL

    /** 取得內建花牌的既定排序索引；非內建花牌回傳 null。 */
    fun orderIndex(tile: Tile): Int? = (tile as? Tile.Extension)?.typeId?.let(ALL::indexOf)?.takeIf { it >= 0 }
}
