package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.honor

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.RiichiFuuro
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuType
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/**
 * 字牌役檢測器。
 *
 * 包含以下役種：
 * - 場風 (Bakaze/RoundWind)：手牌中有與圈風相同的風牌刻子/槓
 * - 自風 (Tonmyakze/SeatWind)：手牌中有與自風相同的風牌刻子/槓
 * - 役牌 (Yakuhai)：手牌中有三元牌（白、發、中）的刻子/槓，每種三元牌各自是一個役種
 *
 * @param handTiles 手牌中的牌（不含副露、不含和牌張，不含赤寶牌標記）。
 * @param winningTile 和牌張；靠它補成的刻子同樣成立字牌役，因此必須一併統計。
 * @param fuuro 副露列表。
 * @param roundWind 圈風。
 * @param seatWind 自風。
 * @return 包含所有字牌役結果的列表。
 */
fun calculateRiichiHonorYaku(
    handTiles: List<Tile>,
    winningTile: Tile,
    fuuro: List<RiichiFuuro>,
    roundWind: Wind,
    seatWind: Wind,
): List<RiichiYakuResult> {
    val results = mutableListOf<RiichiYakuResult>()

    // 收集所有牌（手牌 + 和牌張 + 副露中的牌）
    val allTiles = handTiles.toMutableList()
    allTiles.add(winningTile)
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
        results.add(RiichiYakuResult.han(RiichiYakuType.RoundWind, 1))
    }

    // 檢測自風
    val seatWindTile = when (seatWind) {
        Wind.EAST -> Tile.Honor.East
        Wind.SOUTH -> Tile.Honor.South
        Wind.WEST -> Tile.Honor.West
        Wind.NORTH -> Tile.Honor.North
    }
    if ((honorCounts[seatWindTile] ?: 0) >= 3) {
        results.add(RiichiYakuResult.han(RiichiYakuType.SeatWind, 1))
    }

    // 檢測役牌（三元牌）
    val dragonYaku = listOf(
        Tile.Honor.White to RiichiYakuType.WhiteDragon,
        Tile.Honor.Green to RiichiYakuType.GreenDragon,
        Tile.Honor.Red to RiichiYakuType.RedDragon,
    )
    for ((dragon, yaku) in dragonYaku) {
        if ((honorCounts[dragon] ?: 0) >= 3) {
            results.add(RiichiYakuResult.han(yaku, 1))
        }
    }

    return results
}
