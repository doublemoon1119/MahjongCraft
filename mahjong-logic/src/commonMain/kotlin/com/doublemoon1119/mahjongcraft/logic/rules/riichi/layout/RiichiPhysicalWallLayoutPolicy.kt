package com.doublemoon1119.mahjongcraft.logic.rules.riichi.layout

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiTableComponents
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiWallProgress
import com.doublemoon1119.mahjongcraft.logic.table.layout.InitialPhysicalWallLayoutContext
import com.doublemoon1119.mahjongcraft.logic.table.layout.InitialPhysicalWallLayoutDecision
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutPolicy
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionContext
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionDecision
import com.doublemoon1119.mahjongcraft.logic.table.layout.ReplenishingReservedWallPhysicalLayoutPolicy
import com.doublemoon1119.mahjongcraft.logic.table.layout.ReservedWallInitialShape
import com.doublemoon1119.mahjongcraft.logic.table.layout.ReservedWallTrackScope

/**
 * 日本麻將（四人或三人）的獨立王牌區、特殊嶺上牌位置與補牌後的補位布局。
 *
 * 王牌最前面的 [rinshanTileCount] 張是嶺上牌，其後是寶牌與裏寶牌指示牌；嶺上牌張數同時是一局最多的補牌次數。
 * 共用的分界與補位幾何由 [ReplenishingReservedWallPhysicalLayoutPolicy] 處理，本類別只提供日麻特有的嶺上牌形狀
 * 與補牌進度。
 *
 * @param rinshanTileCount 規則設定的嶺上牌張數，必須是不小於 2 的偶數。
 * @param sideCount 牌牆面數。
 * @param stacksPerSide 每面牌牆的墩數。
 */
class RiichiPhysicalWallLayoutPolicy(
    rinshanTileCount: Int,
    sideCount: Int = FOUR_PLAYER_SIDE_COUNT,
    stacksPerSide: Int = FOUR_PLAYER_STACKS_PER_SIDE,
) : PhysicalWallLayoutPolicy {
    init {
        require(rinshanTileCount >= MIN_RINSHAN_TILE_COUNT && rinshanTileCount % 2 == 0) {
            "Riichi rinshan tile count must be an even number of at least $MIN_RINSHAN_TILE_COUNT, got $rinshanTileCount"
        }
    }

    /** 日麻王牌布局使用的共用保留區引擎。 */
    private val delegate = ReplenishingReservedWallPhysicalLayoutPolicy(
        maximumSupplementalDrawCount = rinshanTileCount,
        initialShape = ReservedWallInitialShape.SPLIT_FIRST_TWO,
        trackScope = ReservedWallTrackScope.SINGLE_SIDE,
        sideCount = sideCount,
        stacksPerSide = stacksPerSide,
        reservedWallGapStacks = RESERVED_WALL_GAP_STACKS,
        completedDrawCount = { state ->
            state.components[RiichiTableComponents.WALL_PROGRESS]?.totalSupplementalDrawCount
                ?: INVALID_DRAW_COUNT
        },
        invalidStateReasonId = INVALID_STATE_REASON_ID,
        noTrackReasonId = NO_COLLISION_FREE_TRACK_REASON_ID,
        invalidTransitionReasonId = INVALID_TRANSITION_REASON_ID,
    )

    /** 建立帶王牌分界與特殊嶺上牌位置的開局布局。 */
    override fun createInitialLayout(context: InitialPhysicalWallLayoutContext): InitialPhysicalWallLayoutDecision = delegate.createInitialLayout(context)

    /** 依日麻補牌進度解析王牌區補位。 */
    override fun resolveTransition(context: PhysicalWallLayoutTransitionContext): PhysicalWallLayoutTransitionDecision = delegate.resolveTransition(context)

    /** 本局已完成的補牌次數（槓與拔北合計）。 */
    private val RiichiWallProgress.totalSupplementalDrawCount: Int
        get() = completedSupplementalDrawCount + completedNorthDrawCount

    companion object {
        /** 王牌最少需要的嶺上牌張數。 */
        private const val MIN_RINSHAN_TILE_COUNT = 2

        /** 缺少日麻牌牆進度時使用的無效值。 */
        private const val INVALID_DRAW_COUNT = -1

        /** 四人日麻的牌牆面數。 */
        private const val FOUR_PLAYER_SIDE_COUNT = 4

        /** 四人日麻每面的墩數。 */
        private const val FOUR_PLAYER_STACKS_PER_SIDE = 17

        /** 日麻既有王牌分界的位移墩數。 */
        private const val RESERVED_WALL_GAP_STACKS = 1.5

        /** 初始牌牆缺少日麻嶺上牌結構時的拒絕原因。 */
        const val INVALID_STATE_REASON_ID: String = "mahjongcraft:riichi/invalid_physical_wall_state"

        /** 開門面沒有足夠連續且無占用的保留牌軌道時的拒絕原因。 */
        const val NO_COLLISION_FREE_TRACK_REASON_ID: String = "mahjongcraft:riichi/no_collision_free_reserved_wall_track"

        /** 槓前後牌牆或補牌次數不符合日麻 transition 契約時的拒絕原因。 */
        const val INVALID_TRANSITION_REASON_ID: String = "mahjongcraft:riichi/invalid_physical_wall_transition"
    }
}
