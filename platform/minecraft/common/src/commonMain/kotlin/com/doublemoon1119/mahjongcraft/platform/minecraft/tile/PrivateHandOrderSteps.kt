package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import kotlin.uuid.Uuid

/**
 * 依一步的前後排列，算出本人手牌排列在這一步中依序生效的項目，時間點對齊其他人看到的牌張動作。
 *
 * 有牌離開立牌或摸牌位時，離開動作開始時先把那些牌換成空格，其餘牌不動；讓位動作開始時換成這一步之後的排列。這一步才
 * 從牌牆進來的牌已在之後的排列中，格位先空出來，牌落下時才接管。與上一個排列相同的項目不送。
 */
object PrivateHandOrderSteps {
    /**
     * 依桌況中的手牌與共享擺放算出本人排列。
     *
     * @param ownerId 手牌的主人。
     * @param hand 桌況中的手牌；立牌順序就是本人的理牌順序。
     * @param arrangement 同一份桌況的共享擺放，提供還沒理過的牌；還沒有擺放時為 null。
     * @return 沒有空格的本人排列。
     */
    fun orderOf(
        ownerId: Uuid,
        hand: Hand,
        arrangement: Map<Uuid, ArrangedTile>?,
    ): PrivateHandOrder = PrivateHandOrder(
        standing = hand.tiles.map { it.id },
        drawnId = hand.lastDrawn?.id,
        unsortedIds = arrangement.orEmpty().values
            .filter { it.unsorted && it.row.ownerId == ownerId && it.row.region == TileRegion.HAND }
            .sortedBy { it.orderInRow }
            .map { it.tileId },
    )

    /**
     * 算出這一步的項目。
     *
     * @param before 這一步開始時的排列；之前沒有排列時為 null。
     * @param after 這一步之後的排列。
     * @param departAt 離開動作開始的遊戲時間。
     * @param makeRoomAt 讓位動作開始的遊戲時間。
     * @return 依生效時間排列的項目。
     */
    fun of(
        before: PrivateHandOrder?,
        after: PrivateHandOrder,
        departAt: Long,
        makeRoomAt: Long,
    ): List<PrivateHandOrderEntry> {
        if (before == null) return listOf(PrivateHandOrderEntry(effectiveAt = makeRoomAt, order = after))
        val remaining = (after.standing + after.drawnId).filterNotNullTo(mutableSetOf())
        val leaving = (before.standing + before.drawnId).filterNotNull().filterNot { it in remaining }.toSet()
        val entries = mutableListOf<PrivateHandOrderEntry>()
        var last = before
        if (leaving.isNotEmpty()) {
            last = PrivateHandOrder(
                standing = before.standing.map { id -> id?.takeUnless { it in leaving } },
                drawnId = before.drawnId?.takeUnless { it in leaving },
                unsortedIds = before.unsortedIds.filterNot { it in leaving },
            )
            entries += PrivateHandOrderEntry(effectiveAt = departAt, order = last)
        }
        if (after != last) entries += PrivateHandOrderEntry(effectiveAt = makeRoomAt, order = after)
        return entries
    }
}
