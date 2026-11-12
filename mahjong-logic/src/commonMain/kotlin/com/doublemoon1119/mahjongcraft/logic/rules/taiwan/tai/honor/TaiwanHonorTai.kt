package com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.honor

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiResult
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/**
 * 計算與字牌有關的台：門風、圈風、三元、小三元、大三元、小四喜、大四喜與字一色。
 *
 * 小三元或大三元成立時不再計三元台；小四喜或大四喜成立時不再計門風與圈風。
 *
 * @param triplets 這次拆法中全部刻子（含副露的碰與槓）的牌面。
 * @param pair 這次拆法的雀頭。
 * @param allFaces 手牌、副露與胡牌的全部牌面。
 * @param seatWind 玩家門風。
 * @param roundWind 圈風。
 * @return 成立的台。
 */
fun calculateTaiwanHonorTai(
    triplets: List<Tile>,
    pair: Tile,
    allFaces: List<Tile>,
    seatWind: Wind,
    roundWind: Wind,
): List<TaiwanTaiResult> {
    val results = mutableListOf<TaiwanTaiResult>()
    val dragonCount = DRAGONS.count { it in triplets }
    val littleDragons = dragonCount == 2 && pair in DRAGONS
    val bigDragons = dragonCount == 3
    val windCount = WINDS.count { it in triplets }
    val littleWinds = windCount == 3 && pair in WINDS
    val bigWinds = windCount == 4
    if (!littleWinds && !bigWinds) {
        if (WINDS[seatWind.ordinal] in triplets) results += TaiwanTaiResult(TaiwanTaiType.SeatWind, 1)
        if (WINDS[roundWind.ordinal] in triplets) results += TaiwanTaiResult(TaiwanTaiType.RoundWind, 1)
    }
    if (!littleDragons && !bigDragons && dragonCount > 0) results += TaiwanTaiResult(TaiwanTaiType.Dragon, dragonCount)
    if (bigDragons) {
        results += TaiwanTaiResult(TaiwanTaiType.BigDragons, 8)
    } else if (littleDragons) {
        results += TaiwanTaiResult(TaiwanTaiType.LittleDragons, 4)
    }
    if (bigWinds) {
        results += TaiwanTaiResult(TaiwanTaiType.BigWinds, 16)
    } else if (littleWinds) {
        results += TaiwanTaiResult(TaiwanTaiType.LittleWinds, 8)
    }
    if (allFaces.none { it is Tile.Numeric }) results += TaiwanTaiResult(TaiwanTaiType.AllHonors, 16)
    return results
}

/** 風牌，依門風順序排列。 */
private val WINDS: List<Tile> = listOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North)

/** 三元牌。 */
private val DRAGONS: List<Tile> = listOf(Tile.Honor.Red, Tile.Honor.Green, Tile.Honor.White)
