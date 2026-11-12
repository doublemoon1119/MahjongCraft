package com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.flower

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiResult
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/**
 * 計算胡牌時花牌的台：正花每張一台，湊齊四季或四君子的花槓每組一台。
 *
 * @param flowers 已補出的花牌面。
 * @param seatWind 玩家門風；東家對應春與梅，依此類推。
 * @return 成立的台。
 */
fun calculateTaiwanFlowerTai(flowers: List<Tile>, seatWind: Wind): List<TaiwanTaiResult> {
    val typeIds = flowers.filterIsInstance<Tile.Extension>().mapTo(mutableSetOf<TileTypeId>()) { it.typeId }
    val seatFlowers = listOf(SEASONS[seatWind.ordinal], PLANTS[seatWind.ordinal]).count { it in typeIds }
    val completeSets = listOf(SEASONS, PLANTS).count { typeIds.containsAll(it) }
    return listOfNotNull(
        seatFlowers.takeIf { it > 0 }?.let { TaiwanTaiResult(TaiwanTaiType.SeatFlower, it) },
        completeSets.takeIf { it > 0 }?.let { TaiwanTaiResult(TaiwanTaiType.FlowerSet, it) },
    )
}

/**
 * 八仙過海的台：一人湊齊八張花牌時成立的獨立花胡。
 *
 * @return 八仙過海的台。
 */
fun calculateTaiwanEightFlowers(): TaiwanTaiResult = TaiwanTaiResult(TaiwanTaiType.EightFlowers, 8)

/**
 * 七搶一的台：持有七張花牌的玩家搶到第八張時成立的獨立花胡。
 *
 * @return 七搶一的台。
 */
fun calculateTaiwanSevenFlowers(): TaiwanTaiResult = TaiwanTaiResult(TaiwanTaiType.SevenFlowers, 8)

/** 依春夏秋冬排列的四季花。 */
private val SEASONS = listOf(TaiwanTileTypes.SPRING, TaiwanTileTypes.SUMMER, TaiwanTileTypes.AUTUMN, TaiwanTileTypes.WINTER)

/** 依梅蘭菊竹排列的四君子花。 */
private val PLANTS = listOf(TaiwanTileTypes.PLUM, TaiwanTileTypes.ORCHID, TaiwanTileTypes.CHRYSANTHEMUM, TaiwanTileTypes.BAMBOO)
