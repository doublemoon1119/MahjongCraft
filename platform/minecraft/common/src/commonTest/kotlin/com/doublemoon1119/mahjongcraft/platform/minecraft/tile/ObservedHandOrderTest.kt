package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.HandSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiTileOrder
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證本機替別人排手牌時只依看得到的牌面排序，看不到的牌維持身分順序，並從共享牌排位置找出還沒理過的牌。 */
class ObservedHandOrderTest {
    /** 看得到牌面的立牌依規則牌序排列，剛摸的牌留在摸牌位。 */
    @Test
    fun `visible standing tiles follow the rule order and the drawn tile stays apart`() {
        val east = tile(Tile.Honor.East)
        val nineCharacters = tile(Tile.Numeric(Tile.Suit.Character, 9))
        val oneDots = tile(Tile.Numeric(Tile.Suit.Dot, 1))
        val oneCharacters = tile(Tile.Numeric(Tile.Suit.Character, 1))
        val drawn = tile(Tile.Honor.Red)
        val hand = HandSnapshot(standingTiles = listOf(east, nineCharacters, oneDots, oneCharacters, drawn), lastDrawn = drawn)

        val order = ObservedHandOrder.arrange(hand, RiichiTileOrder, sharedTargets = emptyMap())

        val expected = listOf(oneCharacters, nineCharacters, oneDots, east)
            .sortedWith(compareBy<IdentifiedTileSnapshot, Tile>(RiichiTileOrder) { checkNotNull(it.tile) })
            .map { it.id }
        assertEquals(expected, order?.standing)
        assertEquals(drawn.id, order?.drawnId)
    }

    /** 看得到牌面的立牌排在左邊並依規則牌序排列，看不到的立牌接在右邊並依牌張身分排列，看不到的摸牌仍在摸牌位。 */
    @Test
    fun `visible tiles go left in rule order and hidden tiles keep the identity order`() {
        val east = tile(Tile.Honor.East)
        val oneCharacters = tile(Tile.Numeric(Tile.Suit.Character, 1))
        val hidden = List(3) { IdentifiedTileSnapshot(Uuid.random(), face = null) }
        val hiddenDrawn = IdentifiedTileSnapshot(Uuid.random(), face = null)
        val hand = HandSnapshot(standingTiles = listOf(hidden[0], east, hidden[1], oneCharacters, hidden[2], hiddenDrawn), lastDrawn = hiddenDrawn)

        val order = ObservedHandOrder.arrange(hand, RiichiTileOrder, sharedTargets = emptyMap())

        assertEquals(listOf(oneCharacters.id, east.id) + hidden.map { it.id }.sortedBy(Uuid::toString), order?.standing)
        assertEquals(hiddenDrawn.id, order?.drawnId)
    }

    /** 沒有任何一張看得到牌面的立牌時交給共享牌排。 */
    @Test
    fun `hands without visible standing tiles are left to the shared layout`() {
        val hidden = IdentifiedTileSnapshot(Uuid.random(), face = null)
        val visibleDrawn = tile(Tile.Honor.East)

        assertNull(ObservedHandOrder.arrange(HandSnapshot(listOf(hidden)), RiichiTileOrder, emptyMap()))
        assertNull(ObservedHandOrder.arrange(HandSnapshot(listOf(hidden, visibleDrawn), lastDrawn = visibleDrawn), RiichiTileOrder, emptyMap()))
        assertNull(ObservedHandOrder.arrange(HandSnapshot(emptyList()), RiichiTileOrder, emptyMap()))
    }

    /** 沿牌排方向依牌張身分排列的開頭是已理好的牌，之後接上的牌是還沒理過的牌。 */
    @Test
    fun `tiles appended after the identity ordered row are unsorted`() {
        val ids = List(4) { Uuid.random() }.sortedBy(Uuid::toString)
        val appended = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val row = ids + appended
        val targets = row.withIndex().associate { (index, id) -> id to standing(x = index.toDouble(), yaw = 0f) }

        assertEquals(listOf(appended), ObservedHandOrder.unsortedFromPositions(row.shuffled(), targets))
        assertEquals(emptyList(), ObservedHandOrder.unsortedFromPositions(ids, targets))
    }

    /** 牌排轉向時沿牌排方向計算順序，不依世界座標軸。 */
    @Test
    fun `row order follows the row direction`() {
        val ids = List(3) { Uuid.random() }.sortedBy(Uuid::toString)
        val appended = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val row = ids + appended
        val targets = row.withIndex().associate { (index, id) -> id to standing(z = index.toDouble(), yaw = 90f) }

        assertEquals(listOf(appended), ObservedHandOrder.unsortedFromPositions(row, targets))
    }

    /** 有牌不在立起的牌排上時，不推算還沒理過的牌。 */
    @Test
    fun `missing or lying tiles give no unsorted tiles`() {
        val ids = List(3) { Uuid.random() }
        val standingTargets = ids.withIndex().associate { (index, id) -> id to standing(x = index.toDouble(), yaw = 0f) }

        assertEquals(emptyList(), ObservedHandOrder.unsortedFromPositions(ids, standingTargets - ids.last()))
        assertEquals(
            expected = emptyList(),
            actual = ObservedHandOrder.unsortedFromPositions(ids, standingTargets + (ids.last() to standingTargets.getValue(ids.last()).copy(poseDegrees = 90f))),
        )
    }

    /**
     * 建立看得到牌面的牌。
     *
     * @param value 牌面。
     * @return 牌張快照。
     */
    private fun tile(value: Tile) = IdentifiedTileSnapshot(Uuid.random(), TileFaceView(value))

    /**
     * 建立立起的共享牌位。
     *
     * @param x 世界 X 座標。
     * @param z 世界 Z 座標。
     * @param yaw 牌排朝向。
     * @return 共享牌位。
     */
    private fun standing(
        x: Double = 0.0,
        z: Double = 0.0,
        yaw: Float,
    ) = TileDisplayTransform(DiceAnimationVector(x, 0.0, z), yawDegrees = yaw, poseDegrees = 0f)
}
