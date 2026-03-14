package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.dora

import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.domain.util.withoutRed

/**
 * 寶牌 (Dora) 役種檢測器。
 *
 * 根據寶牌指示牌計算寶牌提供的番數。
 * 寶牌指示牌 +1 即為寶牌，例如：
 * - 1 萬 → 2 萬為寶牌
 * - 9 萬 → 1 萬為寶牌（循環）
 * - 東 → 南為寶牌
 * - 白 → 發為寶牌
 *
 * @param hand 玩家手牌（包含立牌與副露）。
 * @param winningTile 胡牌張（計入寶牌計算）。
 * @param doraIndicators 寶牌指示牌列表。
 * @param isRiichi 是否為立直（影響裏寶牌計算）。
 * @param uraDoraIndicators 裏寶牌指示牌列表（立直時）。
 * @return 寶牌番數結果。
 */
fun calculateDora(
    hand: Hand,
    winningTile: Tile,
    doraIndicators: List<Tile>,
    isRiichi: Boolean,
    uraDoraIndicators: List<Tile> = emptyList()
): YakuResult {
    val allTiles = hand.allTiles.map { it.tile } + winningTile

    val doraCount = doraIndicators.sumOf { indicator ->
        val doraTile = getNextDora(indicator)
        allTiles.count { it.withoutRed == doraTile }
    }

    val uraDoraCount = if (isRiichi) {
        uraDoraIndicators.sumOf { indicator ->
            val doraTile = getNextDora(indicator)
            allTiles.count { it.withoutRed == doraTile }
        }
    } else {
        0
    }

    val totalDora = doraCount + uraDoraCount

    return YakuResult.han(YakuType.Dora, totalDora)
}

/**
 * 取得寶牌指示牌的下一張牌作為寶牌。
 *
 * @param indicator 寶牌指示牌。
 * @return 對應的寶牌。
 */
private fun getNextDora(indicator: Tile): Tile {
    return when (indicator) {
        // 數牌：循環 1-9
        is Tile.Numeric -> {
            val nextValue = if (indicator.value == 9) 1 else indicator.value + 1
            Tile.Numeric(indicator.suit, nextValue, isRed = false)
        }
        // 字牌：東→南→西→北→東（循環）
        is Tile.Honor.East -> Tile.Honor.South
        is Tile.Honor.South -> Tile.Honor.West
        is Tile.Honor.West -> Tile.Honor.North
        is Tile.Honor.North -> Tile.Honor.East
        // 三元牌：白→發→中→白（循環）
        is Tile.Honor.White -> Tile.Honor.Green
        is Tile.Honor.Green -> Tile.Honor.Red
        is Tile.Honor.Red -> Tile.Honor.White
        // 花牌在日麻中不作為寶牌指示牌
        is Tile.Flower -> indicator
    }
}
