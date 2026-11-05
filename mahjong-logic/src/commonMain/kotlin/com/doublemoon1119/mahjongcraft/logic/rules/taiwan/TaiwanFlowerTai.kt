package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/** 只取決於已補出花牌的台數，與手牌牌型無關。 */
object TaiwanFlowerTai {
    /** 依春夏秋冬排列的四季花。 */
    private val SEASONS = listOf(TaiwanTileTypes.SPRING, TaiwanTileTypes.SUMMER, TaiwanTileTypes.AUTUMN, TaiwanTileTypes.WINTER)

    /** 依梅蘭菊竹排列的四君子花。 */
    private val PLANTS = listOf(TaiwanTileTypes.PLUM, TaiwanTileTypes.ORCHID, TaiwanTileTypes.CHRYSANTHEMUM, TaiwanTileTypes.BAMBOO)

    /**
     * 計算與門風對應的花牌張數；東家對應春與梅，依此類推。
     *
     * @param flowers 已補出的花牌面。
     * @param seatWind 玩家門風。
     * @return 對應門風的花牌張數。
     */
    fun seatFlowerCount(flowers: List<Tile>, seatWind: Wind): Int {
        val typeIds = typeIds(flowers)
        return listOf(SEASONS[seatWind.ordinal], PLANTS[seatWind.ordinal]).count { it in typeIds }
    }

    /**
     * 計算湊齊的整組花牌數。
     *
     * @param flowers 已補出的花牌面。
     * @return 湊齊四季或四君子的組數。
     */
    fun completeSetCount(flowers: List<Tile>): Int {
        val typeIds = typeIds(flowers)
        return listOf(SEASONS, PLANTS).count { typeIds.containsAll(it) }
    }

    /**
     * 取出花牌的牌種識別碼。
     *
     * @param flowers 已補出的花牌面。
     * @return 花牌的牌種識別碼。
     */
    private fun typeIds(flowers: List<Tile>): Set<TileTypeId> = flowers.filterIsInstance<Tile.Extension>().mapTo(mutableSetOf()) { it.typeId }
}
