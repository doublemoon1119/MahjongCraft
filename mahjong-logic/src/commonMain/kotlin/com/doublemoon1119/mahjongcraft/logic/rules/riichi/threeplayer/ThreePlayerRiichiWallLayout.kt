package com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayoutResult
import com.doublemoon1119.mahjongcraft.logic.table.layout.WallRingLayoutSupport
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening

/**
 * 三人日本麻將的 108 張牌牆布局：三面牌牆分別在三位玩家面前，每面 18 墩、每墩 2 層。
 *
 * 王牌墩數依 [ThreePlayerRiichiRuleConfig.deadTileCount] 換算（14 張＝7 墩）。
 *
 * @property config 決定王牌墩數的三人日本麻將規則配置。
 */
class ThreePlayerRiichiWallLayout(private val config: ThreePlayerRiichiRuleConfig) : TileWallLayout {
    override fun resolve(shuffledTiles: List<IdentifiedTile>, opening: WallOpening): TileWallLayoutResult = WallRingLayoutSupport.resolve(
        shuffledTiles = shuffledTiles,
        opening = opening,
        stacksPerSide = STACKS_PER_SIDE,
        deadWallStacks = config.deadTileCount / 2,
        sideCount = SIDE_COUNT,
    )

    companion object {
        /** 牌牆面數，與玩家人數相同。 */
        const val SIDE_COUNT = 3

        /** 每面牌牆的墩數。 */
        const val STACKS_PER_SIDE = 18
    }
}
