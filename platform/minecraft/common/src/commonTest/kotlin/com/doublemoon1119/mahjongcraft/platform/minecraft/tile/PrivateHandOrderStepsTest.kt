package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證本人手牌排列在一步中依序生效的項目。 */
class PrivateHandOrderStepsTest {
    /** 測試用的第一張牌。 */
    private val a = Uuid.parse("00000000-0000-0000-0000-00000000000a")

    /** 測試用的第二張牌。 */
    private val b = Uuid.parse("00000000-0000-0000-0000-00000000000b")

    /** 測試用的第三張牌。 */
    private val c = Uuid.parse("00000000-0000-0000-0000-00000000000c")

    /** 測試用的摸牌。 */
    private val d = Uuid.parse("00000000-0000-0000-0000-00000000000d")

    /** 打出立牌中的牌：離開時先留空格，讓位時摸到的牌才插進去。 */
    @Test
    fun `discarding from the hand leaves a gap before the drawn tile joins`() {
        val entries = PrivateHandOrderSteps.of(
            before = order(listOf(a, b, c), drawnId = d),
            after = order(listOf(a, d, c), drawnId = null),
            departAt = 10L,
            makeRoomAt = 18L,
        )

        assertEquals(
            listOf(
                PrivateHandOrderEntry(effectiveAt = 10L, order = order(listOf(a, null, c), drawnId = d)),
                PrivateHandOrderEntry(effectiveAt = 18L, order = order(listOf(a, d, c), drawnId = null)),
            ),
            entries,
        )
    }

    /** 打出摸到的牌：離開後的排列已經是最後的排列，不再重複送出。 */
    @Test
    fun `discarding the drawn tile sends only the departure`() {
        val entries = PrivateHandOrderSteps.of(
            before = order(listOf(a, b), drawnId = d),
            after = order(listOf(a, b), drawnId = null),
            departAt = 10L,
            makeRoomAt = 18L,
        )

        assertEquals(listOf(PrivateHandOrderEntry(effectiveAt = 10L, order = order(listOf(a, b), drawnId = null))), entries)
    }

    /** 沒有牌離開時只有讓位時的項目；排列沒變時不送。 */
    @Test
    fun `steps without departures send at most the new order`() {
        val drawing = PrivateHandOrderSteps.of(before = order(listOf(a, b), drawnId = null), after = order(listOf(a, b), drawnId = d), departAt = 0L, makeRoomAt = 0L)
        val unchanged = PrivateHandOrderSteps.of(before = order(listOf(a, b), drawnId = d), after = order(listOf(a, b), drawnId = d), departAt = 0L, makeRoomAt = 0L)
        val first = PrivateHandOrderSteps.of(before = null, after = order(listOf(a, b), drawnId = null), departAt = 3L, makeRoomAt = 5L)

        assertEquals(listOf(PrivateHandOrderEntry(effectiveAt = 0L, order = order(listOf(a, b), drawnId = d))), drawing)
        assertEquals(emptyList(), unchanged)
        assertEquals(listOf(PrivateHandOrderEntry(effectiveAt = 5L, order = order(listOf(a, b), drawnId = null))), first)
    }

    /** 本人排列取自手牌順序與摸牌位；還沒理過的牌只取自己立牌中的，依共享位置排列。 */
    @Test
    fun `order of a hand keeps its own unsorted tiles in shared order`() {
        val (first, second, third) = List(3) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, it + 1)) }
        val drawn = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val ownerId = Uuid.parse("00000000-0000-0000-0000-000000000100")
        val otherId = Uuid.parse("00000000-0000-0000-0000-000000000200")
        val hand = TileRowKey(region = TileRegion.HAND, ownerId = ownerId, group = null)
        val arrangement = listOf(
            arranged(first.id, hand, order = 0, unsorted = false),
            arranged(third.id, hand, order = 2, unsorted = true),
            arranged(second.id, hand, order = 1, unsorted = true),
            arranged(a, TileRowKey(region = TileRegion.HAND, ownerId = otherId, group = null), order = 3, unsorted = true),
        ).associateBy { it.tileId }

        val order = PrivateHandOrderSteps.orderOf(ownerId, Hand(tiles = listOf(first, second, third), lastDrawn = drawn), arrangement)

        assertEquals(order(listOf(first.id, second.id, third.id), drawnId = drawn.id, unsortedIds = listOf(second.id, third.id)), order)
    }

    /** 組成排列。 */
    private fun order(
        standing: List<Uuid?>,
        drawnId: Uuid?,
        unsortedIds: List<Uuid> = emptyList(),
    ) = PrivateHandOrder(standing = standing, drawnId = drawnId, unsortedIds = unsortedIds)

    /** 組成只有排序資料有意義的擺放。 */
    private fun arranged(
        tileId: Uuid,
        row: TileRowKey,
        order: Int,
        unsorted: Boolean,
    ) = ArrangedTile(
        tileId = tileId,
        row = row,
        orderInRow = order,
        placement = MahjongTileWallPlacement(0.0, 0.0, 0.0, 0f),
        pose = TilePose.UPRIGHT,
        unsorted = unsorted,
    )
}
