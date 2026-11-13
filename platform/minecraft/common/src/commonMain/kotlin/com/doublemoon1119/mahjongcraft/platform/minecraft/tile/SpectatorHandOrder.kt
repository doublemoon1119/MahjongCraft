package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.HandSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.uuid.Uuid

/**
 * 旁觀者在本機替看得到牌面的手牌排出的順序：立牌依規則牌序，剛摸的牌留在獨立的摸牌位。
 *
 * 只改變旁觀者自己看到的位置；共享牌排仍依牌張身分排列，不洩漏玩家的私人理牌。
 */
object SpectatorHandOrder {
    /**
     * 依規則牌序排出手牌。
     *
     * @param hand 旁觀者收到的手牌快照。
     * @param tileOrder 該桌規則的牌序。
     * @param sharedTargets 共享牌排上各牌的位置，用來找出共享牌排接在最右邊、還沒理過的牌。
     * @return 手牌排列；有任何一張手牌看不到牌面，或沒有手牌時為 null。
     */
    fun arrange(
        hand: HandSnapshot,
        tileOrder: Comparator<Tile>,
        sharedTargets: Map<Uuid, TileDisplayTransform>,
    ): PrivateHandOrder? {
        val drawn = hand.lastDrawn
        if (drawn != null && drawn.tile == null) return null
        val standing = hand.standingTiles.filterNot { it.id == drawn?.id }.map { tile -> tile.id to (tile.tile ?: return null) }
        if (standing.isEmpty() && drawn == null) return null
        val sorted = standing.sortedWith(compareBy<Pair<Uuid, Tile>, Tile>(tileOrder) { it.second }.thenBy { it.first.toString() }).map { it.first }
        return PrivateHandOrder(
            standing = sorted,
            drawnId = drawn?.id,
            unsortedIds = unsortedFromPositions(standing.map { it.first }, sharedTargets),
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
