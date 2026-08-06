package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.CompletionType
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/**
 * 平和 (Pinfu) 役種檢測器。
 *
 * 平和是立直麻將中最基本的一翻役，條件如下：
 * 1. 必須為門前清（無副露）
 * 2. 所有的面子都是順子（無刻子、槓）
 * 3. 雀頭不是役牌（三元牌、自風牌、場風牌）
 * 4. 听牌型為兩面聽（坦張）
 *
 * @param handStructure 手牌結構。
 * @param isMenzen 是否為門前清。
 * @param roundWind 場風。
 * @param seatWind 自風。
 * @return 平和役種結果，若不符合則返回 null。
 */
fun calculatePinfu(
    handStructure: HandStructure,
    isMenzen: Boolean,
    roundWind: Wind,
    seatWind: Wind,
): YakuResult? {
    val standard = handStructure as? HandStructure.Standard ?: return null

    // 平和必須為門前清
    if (!isMenzen) {
        return null
    }

    // 檢查所有面子是否都是順子
    for (mentsu in standard.mentsus) {
        if (mentsu !is Mentsu.Shuntsu) {
            return null
        }
    }

    // 檢查雀頭是否為役牌（三元牌、自風牌、場風牌）
    val pairTile = standard.pair.tile
    if (isYakuhai(pairTile, roundWind, seatWind)) {
        return null
    }

    // 檢查聽牌型是否為兩面聽，平和限定要兩面聽牌才成立
    if (standard.completionType !is CompletionType.Ryanmen) {
        return null
    }

    return YakuResult.han(YakuType.Pinfu, 1)
}

/**
 * 檢查牌是否為役牌（三元牌、自風牌、場風牌）。
 *
 * @param tile 雀頭牌。
 * @param roundWind 場風。
 * @param seatWind 自風。
 */
private fun isYakuhai(
    tile: Tile,
    roundWind: Wind,
    seatWind: Wind,
): Boolean {
    // 場風牌
    val roundWindTile = when (roundWind) {
        Wind.EAST -> Tile.Honor.East
        Wind.SOUTH -> Tile.Honor.South
        Wind.WEST -> Tile.Honor.West
        Wind.NORTH -> Tile.Honor.North
    }

    // 自風牌
    val seatWindTile = when (seatWind) {
        Wind.EAST -> Tile.Honor.East
        Wind.SOUTH -> Tile.Honor.South
        Wind.WEST -> Tile.Honor.West
        Wind.NORTH -> Tile.Honor.North
    }

    // 役牌
    val yakuTiles = setOf(
        Tile.Honor.White,
        Tile.Honor.Green,
        Tile.Honor.Red,
        roundWindTile,
        seatWindTile,
    )
    return tile in yakuTiles
}
