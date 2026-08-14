package com.doublemoon1119.mahjongcraft.logic.rules.taiwan.layout

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.layout.FourSidedWallLayoutSupport
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayoutResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening

/**
 * 四人台灣麻將的牌牆布局，依是否使用花牌支援 136 或 144 張。
 *
 * 四面牌牆，不含花牌時每面 17 墩（136 張），含花牌時每面 18 墩（144 張）；每面墩數依實際輸入的
 * 牌組張數換算，不假設固定值。王牌墩數依 [TaiwanRuleConfig.deadTileCount] 換算（預設 16 張＝8
 * 墩），與 [FourSidedWallLayoutSupport] 共用的排列邏輯保持單一事實來源。
 *
 * 王牌相對開門缺口的方向與張數，來源：
 * [華人麻將競技聯盟賽事規則](https://cml88.com/%E8%B3%BD%E4%BA%8B%E8%A6%8F%E5%89%87/)（「尾牌留
 * 16 支」）；三骰換算開門位置的公式見
 * [TaiwanWallOpeningPolicy][com.doublemoon1119.mahjongcraft.logic.rules.taiwan.opening.TaiwanWallOpeningPolicy]。
 *
 * @property config 決定王牌墩數的台灣麻將規則配置。
 */
class TaiwanWallLayout(private val config: TaiwanRuleConfig) : TileWallLayout {
    override fun resolve(shuffledTiles: List<IdentifiedTile>, opening: WallOpening): TileWallLayoutResult {
        require(shuffledTiles.size == TILE_COUNT_WITHOUT_FLOWERS || shuffledTiles.size == TILE_COUNT_WITH_FLOWERS) {
            "Taiwan wall layout requires $TILE_COUNT_WITHOUT_FLOWERS or $TILE_COUNT_WITH_FLOWERS tiles, " +
                "got ${shuffledTiles.size}"
        }
        val stacksPerSide = shuffledTiles.size / TILES_PER_STACK / FourSidedWallLayoutSupport.SIDE_COUNT

        return FourSidedWallLayoutSupport.resolve(
            shuffledTiles = shuffledTiles,
            opening = opening,
            stacksPerSide = stacksPerSide,
            deadWallStacks = config.deadTileCount / TILES_PER_STACK,
        )
    }

    private companion object {
        /** 每墩的層數，用於將牌數換算成墩數。 */
        const val TILES_PER_STACK = 2

        /** 不含花牌的總張數。 */
        const val TILE_COUNT_WITHOUT_FLOWERS = 136

        /** 含八張花牌的總張數。 */
        const val TILE_COUNT_WITH_FLOWERS = 144
    }
}
