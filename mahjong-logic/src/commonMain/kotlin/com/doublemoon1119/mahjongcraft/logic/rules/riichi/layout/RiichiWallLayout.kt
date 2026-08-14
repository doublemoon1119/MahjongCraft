package com.doublemoon1119.mahjongcraft.logic.rules.riichi.layout

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.layout.FourSidedWallLayoutSupport
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayoutResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening

/**
 * 四人日本麻將的固定 136 張牌牆布局。
 *
 * 四面牌牆，每面 17 墩、每墩 2 層，共 68 墩 136 張牌。王牌墩數依 [RiichiRuleConfig.deadTileCount]
 * 換算（預設 14 張＝7 墩），與 [FourSidedWallLayoutSupport] 共用的排列邏輯保持單一事實來源，不在此
 * 類別另外寫死一份張數。
 *
 * 王牌相對開門缺口的方向與張數，是依可靠規則來源（WRC／通行日麻慣例：開門缺口往右數 7 墩、14 張
 * 為王牌）核對過的結果。
 *
 * @property config 決定王牌墩數的日本麻將規則配置。
 */
class RiichiWallLayout(private val config: RiichiRuleConfig) : TileWallLayout {
    override fun resolve(shuffledTiles: List<IdentifiedTile>, opening: WallOpening): TileWallLayoutResult = FourSidedWallLayoutSupport.resolve(
        shuffledTiles = shuffledTiles,
        opening = opening,
        stacksPerSide = STACKS_PER_SIDE,
        deadWallStacks = config.deadTileCount / 2,
    )

    private companion object {
        /** 四人日麻固定 136 張，每面牌牆的墩數。 */
        const val STACKS_PER_SIDE = 17
    }
}
