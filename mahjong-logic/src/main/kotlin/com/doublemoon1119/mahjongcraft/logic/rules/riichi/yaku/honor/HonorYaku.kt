package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.honor

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Fuuro
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/**
 * 字牌役檢測器。
 *
 * 包含以下役種：
 * - 場風 (Bakaze/RoundWind)：手牌中有與圈風相同的風牌刻子/槓
 * - 自風 (Tonmyakze/SeatWind)：手牌中有與自風相同的風牌刻子/槓
 * - 役牌 (Yakuhai/Dragon)：手牌中有三元牌（中、發、白）的刻子/槓
 *
 * @param handTiles 手牌中的牌（不含副露，不含赤寶牌標記）。
 * @param fuuro 副露列表。
 * @param roundWind 圈風。
 * @param seatWind 自風。
 * @return 包含所有字牌役結果的列表。
 */
fun calculateHonorYaku(
    handTiles: List<Tile>,
    fuuro: List<Fuuro>,
    roundWind: Wind,
    seatWind: Wind
): List<YakuResult> {
    val results = mutableListOf<YakuResult>()

    // 收集所有牌（手牌 + 副露中的牌）
    val allTiles = handTiles.toMutableList()
    for (f in fuuro) {
        allTiles.addAll(f.mentsu.tiles)
    }

    // 統計字牌數量
    val honorCounts = allTiles
        .filterIsInstance<Tile.Honor>()
        .groupingBy { it }
        .eachCount()

    // 檢測場風
    val roundWindTile = when (roundWind) {
        Wind.EAST -> Tile.Honor.East
        Wind.SOUTH -> Tile.Honor.South
        Wind.WEST -> Tile.Honor.West
        Wind.NORTH -> Tile.Honor.North
    }
    if ((honorCounts[roundWindTile] ?: 0) >= 3) {
        results.add(YakuResult.han(YakuType.RoundWind, 1))
    }

    // 檢測自風
    val seatWindTile = when (seatWind) {
        Wind.EAST -> Tile.Honor.East
        Wind.SOUTH -> Tile.Honor.South
        Wind.WEST -> Tile.Honor.West
        Wind.NORTH -> Tile.Honor.North
    }
    if ((honorCounts[seatWindTile] ?: 0) >= 3) {
        results.add(YakuResult.han(YakuType.SeatWind, 1))
    }

    // 檢測役牌（三元牌）
    val dragonTiles = setOf(Tile.Honor.Red, Tile.Honor.Green, Tile.Honor.White)
    for (dragon in dragonTiles) {
        if ((honorCounts[dragon] ?: 0) >= 3) {
            results.add(YakuResult.han(YakuType.Dragon, 1))
        }
    }

    return results
}
