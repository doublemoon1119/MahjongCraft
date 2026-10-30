package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.dora

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileComponents
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType

/**
 * 赤寶牌 (Aka Dora) 役種檢測器。
 *
 * 帶有 [RiichiTileComponents.RED_DORA] 的牌各算 1 翻，只讀特性，不看牌面或外觀。
 *
 * @param hand 玩家手牌（包含立牌與副露）。
 * @param winningTile 和牌張的完整牌值。
 * @return 赤寶牌番數結果。
 */
fun calculateAkaDora(
    hand: Hand,
    winningTile: TileValue,
): YakuResult {
    val components = hand.allTiles.map { it.components } + winningTile.components
    return YakuResult.han(YakuType.AkaDora, components.count { it[RiichiTileComponents.RED_DORA] != null })
}
