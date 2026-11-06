package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import kotlin.uuid.Uuid

/**
 * 比較前後兩份整桌擺放，決定每張牌在這一步怎麼移動，以及這一步要多久。
 *
 * 區域改變的牌依來源與目的區域選擇動作；同一排內換位置的牌，能維持原本前後順序的最多一組牌平移，其餘越過其他牌的牌
 * 以弧線從上方越過。同一步的動作分成三段依序播放（[TileMotionPhase]）：先離開，再讓位，最後從牌牆摸進來的牌落下；
 * 同一段內的動作同時開始。
 */
object TableStepMotionPlanner {
    /** 牌牆與保留區；兩者之間的移動屬於牌牆重排。 */
    private val WALL_REGIONS = setOf(TileRegion.WALL, TileRegion.RESERVED)

    /**
     * 規劃這一步每張牌的動作。
     *
     * @param previous 這一步開始時的擺放；呈現端還沒有任何擺放時為 null，所有牌直接擺到新位置。
     * @param next 這一步結束時的擺放。
     * @param excludedTileIds 由其他方式呈現的牌，例如依規則分階段重排的牌牆；不規劃動作。
     * @return 位置或姿態有改變的牌的動作。
     */
    fun plan(
        previous: Map<Uuid, ArrangedTile>?,
        next: Map<Uuid, ArrangedTile>,
        excludedTileIds: Set<Uuid> = emptySet(),
    ): List<PlannedTileMotion> {
        val sliding = previous?.let { slidingTileIds(it, next) }.orEmpty()
        return next.values.mapNotNull { after ->
            if (after.tileId in excludedTileIds) return@mapNotNull null
            val before = previous?.get(after.tileId)
                ?: return@mapNotNull PlannedTileMotion(after.tileId, TileMotionKind.PLACE, null, after.row, null, after.placement, null, after.pose)
            val kind = kindOf(before, after, after.tileId in sliding) ?: return@mapNotNull null
            PlannedTileMotion(after.tileId, kind, before.row, after.row, before.placement, after.placement, before.pose, after.pose)
        }
    }

    /**
     * 一個動作播完需要的時間。
     *
     * @param kind 動作種類。
     * @return 動作時長（tick）。
     */
    fun durationTicks(kind: TileMotionKind): Int = when (kind) {
        TileMotionKind.PLACE -> 0
        TileMotionKind.DRAW ->
            MahjongTileTableLayout.DRAW_LIFT_DURATION_TICKS + MahjongTileTableLayout.DRAW_SNAP_GAP_TICKS +
                MahjongTileTableLayout.DRAW_DROP_DURATION_TICKS
        TileMotionKind.FLIGHT -> MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS
        TileMotionKind.SLIDE -> TileMotionAnimationSpec.DEFAULT_DURATION_TICKS
        TileMotionKind.ARC -> MahjongTileTableLayout.ROW_ARC_DURATION_TICKS
        TileMotionKind.TURN -> MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
    }

    /**
     * 動作屬於一步中的哪一段。
     *
     * @param kind 動作種類。
     * @return 播放的段落。
     */
    fun phaseOf(kind: TileMotionKind): TileMotionPhase = when (kind) {
        TileMotionKind.PLACE, TileMotionKind.FLIGHT -> TileMotionPhase.DEPART
        TileMotionKind.SLIDE, TileMotionKind.ARC, TileMotionKind.TURN -> TileMotionPhase.MAKE_ROOM
        TileMotionKind.DRAW -> TileMotionPhase.ARRIVE
    }

    /**
     * 每一段相對這一步開始的時間：每一段在前一段最長的動作完成後開始；沒有動作的段落不佔時間。
     *
     * @param motions 同一步的動作。
     * @return 各段的開始時間（tick）。
     */
    fun phaseStartTicks(motions: List<PlannedTileMotion>): Map<TileMotionPhase, Int> {
        val longestByPhase = motions.groupBy { phaseOf(it.kind) }.mapValues { (_, inPhase) -> inPhase.maxOf { durationTicks(it.kind) } }
        var start = 0
        return TileMotionPhase.entries.associateWith { phase ->
            start.also { start += longestByPhase[phase] ?: 0 }
        }
    }

    /**
     * 一份動作清單播完需要的時間：三段依序播放，每一段取其中最長的動作。
     *
     * @param motions 同一步的動作。
     * @return 這一步的時長（tick）；沒有動作時為 0。
     */
    fun stepDurationTicks(motions: List<PlannedTileMotion>): Int = motions.groupBy { phaseOf(it.kind) }.values.sumOf { inPhase -> inPhase.maxOf { durationTicks(it.kind) } }

    /**
     * 決定一張已在桌上的牌的動作。
     *
     * @param before 這一步開始時的擺放。
     * @param after 這一步結束時的擺放。
     * @param slides 留在同一排時是否屬於維持前後順序的那一組。
     * @return 動作種類；位置與姿態都沒變時為 null。
     */
    private fun kindOf(before: ArrangedTile, after: ArrangedTile, slides: Boolean): TileMotionKind? {
        val from = before.row
        val to = after.row
        return when {
            from == to && before.placement == after.placement -> if (before.pose != after.pose) TileMotionKind.TURN else null
            from == to -> if (from.region in WALL_REGIONS || slides) TileMotionKind.SLIDE else TileMotionKind.ARC
            from.region in WALL_REGIONS && to.region in WALL_REGIONS -> TileMotionKind.SLIDE
            from.region in WALL_REGIONS -> TileMotionKind.DRAW
            from.ownerId == to.ownerId && setOf(from.region, to.region) == setOf(TileRegion.HAND, TileRegion.DRAWN) -> TileMotionKind.ARC
            else -> TileMotionKind.FLIGHT
        }
    }

    /**
     * 每一排中能維持原本前後順序、一起平移的最多一組牌。
     *
     * @param previous 這一步開始時的擺放。
     * @param next 這一步結束時的擺放。
     * @return 平移的牌。
     */
    private fun slidingTileIds(previous: Map<Uuid, ArrangedTile>, next: Map<Uuid, ArrangedTile>): Set<Uuid> = next.values
        .filter { after -> previous[after.tileId]?.row == after.row }
        .groupBy { it.row }
        .values
        .flatMap { stayed ->
            val ordered = stayed.sortedBy { previous.getValue(it.tileId).orderInRow }
            longestIncreasingSubsequence(ordered.map { it.orderInRow }).map { ordered[it].tileId }
        }
        .toSet()

    /**
     * 最長的嚴格遞增子序列。
     *
     * @param values 依原本順序排列的新順序。
     * @return 子序列中各元素在 [values] 的位置。
     */
    internal fun longestIncreasingSubsequence(values: List<Int>): List<Int> {
        if (values.isEmpty()) return emptyList()
        val tails = mutableListOf<Int>()
        val previousIndex = IntArray(values.size) { -1 }
        values.forEachIndexed { index, value ->
            var low = 0
            var high = tails.size
            while (low < high) {
                val middle = (low + high) / 2
                if (values[tails[middle]] < value) low = middle + 1 else high = middle
            }
            if (low > 0) previousIndex[index] = tails[low - 1]
            if (low == tails.size) tails += index else tails[low] = index
        }
        val result = ArrayDeque<Int>()
        var cursor = tails.last()
        while (cursor >= 0) {
            result.addFirst(cursor)
            cursor = previousIndex[cursor]
        }
        return result.toList()
    }
}
