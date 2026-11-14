package com.doublemoon1119.mahjongcraft.logic.rules.taiwan.layout

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.layout.InitialPhysicalWallLayoutContext
import com.doublemoon1119.mahjongcraft.logic.table.layout.InitialPhysicalWallLayoutDecision
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutPolicy
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionContext
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionDecision
import com.doublemoon1119.mahjongcraft.logic.table.layout.ReplenishingReservedWallPhysicalLayoutPolicy
import com.doublemoon1119.mahjongcraft.logic.table.layout.ReservedWallInitialShape
import com.doublemoon1119.mahjongcraft.logic.table.layout.ReservedWallTrackScope
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * 台灣麻將的保留區分界與補花、槓後補牌布局。
 *
 * 16 張保留牌正常疊成八墩。使用花牌時，布局另外預留八次補花與四次槓後補牌的空間；不使用花牌時只預留
 * 四次槓後補牌。實際規則仍會在牌牆耗盡或四槓流局時停止，不會為了填滿預留位置而產生額外補牌。
 *
 * @param config 決定是否使用花牌的台灣麻將設定。
 */
class TaiwanPhysicalWallLayoutPolicy(
    config: TaiwanRuleConfig,
) : PhysicalWallLayoutPolicy {
    /** 台麻保留區布局使用的共用補位引擎。 */
    private val delegate = ReplenishingReservedWallPhysicalLayoutPolicy(
        maximumSupplementalDrawCount = if (config.useFlowerTiles) MAX_FLOWER_AND_KAN_DRAWS else MAX_KAN_DRAWS,
        initialShape = ReservedWallInitialShape.STACKED,
        trackScope = ReservedWallTrackScope.CIRCULAR,
        sideCount = SIDE_COUNT,
        stacksPerSide = if (config.useFlowerTiles) STACKS_PER_SIDE_WITH_FLOWERS else STACKS_PER_SIDE_WITHOUT_FLOWERS,
        reservedWallGapStacks = RESERVED_WALL_GAP_STACKS,
        completedDrawCount = Companion::completedSupplementalDrawCount,
        invalidStateReasonId = INVALID_STATE_REASON_ID,
        noTrackReasonId = NO_COLLISION_FREE_TRACK_REASON_ID,
        invalidTransitionReasonId = INVALID_TRANSITION_REASON_ID,
    )

    /** 建立八墩保留區與一般摸牌區之間具有分界的開局布局。 */
    override fun createInitialLayout(context: InitialPhysicalWallLayoutContext): InitialPhysicalWallLayoutDecision = delegate.createInitialLayout(context)

    /** 依已公開花牌與已完成槓數解析本次保留區補位。 */
    override fun resolveTransition(context: PhysicalWallLayoutTransitionContext): PhysicalWallLayoutTransitionDecision = delegate.resolveTransition(context)

    /** 目前牌牆位置加上保留區之後補牌可能用到的位置。 */
    override fun roundReach(tableState: TableState): Set<TileWallPlacement> = delegate.roundReach(tableState)

    /**
     * 解析觸發動作與補牌分成不同步驟時的保留區補位。
     *
     * @param context 補牌前後桌況與目前布局。
     * @param drawNumber 完成這次補牌後的累計補牌次數。
     * @return 完成的布局與移動階段，或拒絕原因。
     */
    internal fun resolveSupplementalTransition(
        context: PhysicalWallLayoutTransitionContext,
        drawNumber: Int,
    ): PhysicalWallLayoutTransitionDecision = delegate.resolveSupplementalTransition(context, drawNumber)

    companion object {
        /** 一副牌最多八張花牌。 */
        private const val MAX_FLOWER_DRAWS = 8

        /** 台灣麻將的牌牆面數。 */
        private const val SIDE_COUNT = 4

        /** 不使用花牌時每面的墩數。 */
        private const val STACKS_PER_SIDE_WITHOUT_FLOWERS = 17

        /** 使用花牌時每面的墩數。 */
        private const val STACKS_PER_SIDE_WITH_FLOWERS = 18

        /** 四槓流局前最多完成的槓後補牌數。 */
        private const val MAX_KAN_DRAWS = 4

        /** 使用花牌時最多需要的保留區補牌位置。 */
        private const val MAX_FLOWER_AND_KAN_DRAWS = MAX_FLOWER_DRAWS + MAX_KAN_DRAWS

        /** 跨面時仍落在正常牆面朝向的整數分界位移。 */
        private const val RESERVED_WALL_GAP_STACKS = 2.0

        /** 已公開花牌與已完成槓子的合計數，即已從保留區補牌的次數。 */
        internal fun completedSupplementalDrawCount(state: TableState): Int = state.players.sumOf { player ->
            val flowerCount = player.tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]?.tiles?.size ?: 0
            val kanCount = player.hand.melds.count { meld ->
                meld.type == MeldType.OpenKan || meld.type == MeldType.AddedKan || meld.type == MeldType.ClosedKan
            }
            flowerCount + kanCount
        }

        /** 初始保留區張數或形狀不符合台麻契約。 */
        val INVALID_STATE_REASON_ID: String = MahjongCraftMetadata.id("taiwan/invalid_physical_wall_state")

        /** 完整牌牆找不到可容納保留區與補位的連續軌道。 */
        val NO_COLLISION_FREE_TRACK_REASON_ID: String = MahjongCraftMetadata.id("taiwan/no_collision_free_reserved_wall_track")

        /** 補牌前後牌區或累計次數不符合台麻契約。 */
        val INVALID_TRANSITION_REASON_ID: String = MahjongCraftMetadata.id("taiwan/invalid_physical_wall_transition")
    }
}
