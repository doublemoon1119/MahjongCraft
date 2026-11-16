package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.HandSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.uuid.Uuid

/**
 * 在本機替別人的手牌排出的順序：看得到牌面的立牌排在左邊並依規則牌序排列，看不到的立牌接在右邊並維持共享牌排的
 * 牌張身分順序，剛摸的牌留在獨立的摸牌位。例如鷲巢麻將中，對手手上的透明牌集中在左邊。
 *
 * 只改變本機看到的位置；共享牌排仍依牌張身分排列。看得到的牌本來就是公開的，看不到的牌維持與牌面無關的順序，
 * 所以不洩漏玩家的私人理牌，也不透露看不到的牌。
 */
object ObservedHandOrder {
    /**
     * 排出別人的手牌。
     *
     * @param hand 本機收到的手牌快照。
     * @param tileOrder 該桌規則的牌序。
     * @param sharedTargets 共享牌排上各牌的位置，用來找出共享牌排接在最右邊、還沒理過的牌。
     * @return 手牌排列；沒有任何一張看得到牌面的立牌時為 null，交給共享牌排。
     */
    fun arrange(
        hand: HandSnapshot,
        tileOrder: Comparator<Tile>,
        sharedTargets: Map<Uuid, TileDisplayTransform>,
    ): PrivateHandOrder? {
        val drawn = hand.lastDrawn
        val standing = hand.standingTiles.filterNot { it.id == drawn?.id }
        val (visible, hidden) = standing.partition { it.tile != null }
        if (visible.isEmpty()) return null
        val sortedVisible = visible.sortedWith(compareBy<IdentifiedTileSnapshot, Tile>(tileOrder) { checkNotNull(it.tile) }.thenBy { it.id.toString() })
        return PrivateHandOrder(
            standing = sortedVisible.map { it.id } + hidden.map { it.id }.sortedBy(Uuid::toString),
            drawnId = drawn?.id,
            unsortedIds = unsortedFromPositions(standing.map { it.id }, sharedTargets),
        )
    }

    /**
     * 由共享牌排的實際位置，找出接在最右邊、還沒理過的牌。
     *
     * 共享牌排先放依牌張身分排列的已理好的牌，再依補進的順序接上還沒理過的牌；沿牌排方向排列後，依牌張身分排列的最長開頭就是已理好的部分。
     * 還沒理過的牌若剛好也符合身分順序會被算進已理好的部分，兩者的牌排位置相同，不影響推算。
     *
     * @param standingIds 立牌，不含摸牌位。
     * @param sharedTargets 共享牌排上各牌的位置。
     * @return 還沒理過的牌，依共享牌排的順序；有牌不在立起的牌排上時為空列表。
     */
    fun unsortedFromPositions(
        standingIds: List<Uuid>,
        sharedTargets: Map<Uuid, TileDisplayTransform>,
    ): List<Uuid> {
        val targets = standingIds.map { id -> id to (sharedTargets[id]?.takeIf { it.poseDegrees == 0.0f } ?: return emptyList()) }
        val angle = (targets.firstOrNull()?.second?.yawDegrees ?: return emptyList()) * PI / HALF_TURN_DEGREES
        val rowOrder = targets.sortedBy { (_, target) -> target.position.x * cos(angle) + target.position.z * sin(angle) }.map { it.first }
        val sortedPrefix = 1 + rowOrder.zipWithNext().takeWhile { (first, second) -> first.toString() < second.toString() }.size
        return rowOrder.drop(sortedPrefix)
    }

    /** 弧度換算用的半圈角度。 */
    private const val HALF_TURN_DEGREES = 180.0
}
