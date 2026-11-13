package com.doublemoon1119.mahjongcraft.logic.rules.taiwan.layout

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.opening.TaiwanWallOpeningPolicy
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayoutResult
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideArrangement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideReference
import com.doublemoon1119.mahjongcraft.logic.table.layout.WallRingLayoutSupport
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening

/**
 * 四人台灣麻將的牌牆布局，依是否使用花牌支援 136 或 144 張。
 *
 * 四面牌牆，不含花牌時每面 17 墩（136 張），含花牌時每面 18 墩（144 張），依 [TaiwanRuleConfig.useFlowerTiles]
 * 決定。規則保留區墩數依 [TaiwanRuleConfig.reservedTileCount] 換算（預設 16 張＝8
 * 墩），與 [WallRingLayoutSupport] 共用的排列邏輯保持單一事實來源。
 *
 * 保留區相對開門缺口的方向與張數，來源：
 * [華人麻將競技聯盟賽事規則](https://cml88.com/%E8%B3%BD%E4%BA%8B%E8%A6%8F%E5%89%87/)（「尾牌留
 * 16 支」）；三骰換算開門位置的公式見 [TaiwanWallOpeningPolicy]。
 *
 * @property config 決定保留區墩數的台灣麻將規則配置。
 */
class TaiwanWallLayout(private val config: TaiwanRuleConfig) : TileWallLayout {
    /** 依序取得莊家面前、左側、對側與右側的四面牌牆位置。 */
    override fun sideArrangement(dealerSeatIndex: Int): TileWallSideArrangement = TileWallSideArrangement(
        reference = TileWallSideReference.DEALER,
        quarterTurns = listOf(0, 3, 2, 1),
    )

    override fun sideStackCounts(): List<Int> = List(WallRingLayoutSupport.FOUR_SIDE_COUNT) { stacksPerSide }

    override fun resolve(shuffledTiles: List<IdentifiedTile>, opening: WallOpening): TileWallLayoutResult = WallRingLayoutSupport.resolve(
        shuffledTiles = shuffledTiles,
        opening = opening,
        stacksPerSide = stacksPerSide,
        reservedWallStacks = config.reservedTileCount / TILES_PER_STACK,
    )

    /** 每面牌牆的墩數。 */
    private val stacksPerSide: Int get() = if (config.useFlowerTiles) STACKS_WITH_FLOWERS else STACKS_WITHOUT_FLOWERS

    private companion object {
        /** 每墩的層數，用於將牌數換算成墩數。 */
        const val TILES_PER_STACK = 2

        /** 不含花牌（136 張）時每面牌牆的墩數。 */
        const val STACKS_WITHOUT_FLOWERS = 17

        /** 含八張花牌（144 張）時每面牌牆的墩數。 */
        const val STACKS_WITH_FLOWERS = 18
    }
}
