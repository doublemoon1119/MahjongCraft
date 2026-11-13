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

/** 驗證旁觀者本機的手牌排列只用看得到的牌面，並從共享牌排位置找出還沒理過的牌。 */
class SpectatorHandOrderTest {
    /** 看得到牌面的立牌依規則牌序排列，剛摸的牌留在摸牌位。 */
    @Test
    fun `visible standing tiles follow the rule order and the drawn tile stays apart`() {
        val east = tile(Tile.Honor.East)
        val nineCharacters = tile(Tile.Numeric(Tile.Suit.Character, 9))
        val oneDots = tile(Tile.Numeric(Tile.Suit.Dot, 1))
        val oneCharacters = tile(Tile.Numeric(Tile.Suit.Character, 1))
        val drawn = tile(Tile.Honor.Red)
        val hand = HandSnapshot(standingTiles = listOf(east, nineCharacters, oneDots, oneCharacters, drawn), lastDrawn = drawn)

        val order = SpectatorHandOrder.arrange(hand, RiichiTileOrder, sharedTargets = emptyMap())

        val expected = listOf(oneCharacters, nineCharacters, oneDots, east)
            .sortedWith(compareBy<IdentifiedTileSnapshot, Tile>(RiichiTileOrder) { checkNotNull(it.tile) })
            .map { it.id }
        assertEquals(expected, order?.standing)
        assertEquals(drawn.id, order?.drawnId)
    }

    /** 有任何一張看不到牌面的手牌不由旁觀者排列。 */
    @Test
    fun `hands with hidden faces are left to the shared layout`() {
        val hidden = IdentifiedTileSnapshot(Uuid.random(), face = null)
        val visible = tile(Tile.Honor.East)

        assertNull(SpectatorHandOrder.arrange(HandSnapshot(listOf(visible, hidden)), RiichiTileOrder, emptyMap()))
        assertNull(SpectatorHandOrder.arrange(HandSnapshot(listOf(visible, hidden), lastDrawn = hidden), RiichiTileOrder, emptyMap()))
        assertNull(SpectatorHandOrder.arrange(HandSnapshot(emptyList()), RiichiTileOrder, emptyMap()))
    }

    /** 沿牌排方向依牌張身分排列的開頭是已理好的牌，之後接上的牌是還沒理過的牌。 */
    @Test
    fun `tiles appended after the identity ordered row are unsorted`() {
        val ids = List(4) { Uuid.random() }.sortedBy(Uuid::toString)
        val appended = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val row = ids + appended
        val targets = row.withIndex().associate { (index, id) -> id to standing(x = index.toDouble(), yaw = 0f) }

        assertEquals(listOf(appended), SpectatorHandOrder.unsortedFromPositions(row.shuffled(), targets))
        assertEquals(emptyList(), SpectatorHandOrder.unsortedFromPositions(ids, targets))
    }

    /** 牌排轉向時沿牌排方向計算順序，不依世界座標軸。 */
    @Test
    fun `row order follows the row direction`() {
        val ids = List(3) { Uuid.random() }.sortedBy(Uuid::toString)
        val appended = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val row = ids + appended
        val targets = row.withIndex().associate { (index, id) -> id to standing(z = index.toDouble(), yaw = 90f) }

        assertEquals(listOf(appended), SpectatorHandOrder.unsortedFromPositions(row, targets))
    }

    /** 有牌不在立起的牌排上時，不推算還沒理過的牌。 */
    @Test
    fun `missing or lying tiles give no unsorted tiles`() {
        val ids = List(3) { Uuid.random() }
        val standingTargets = ids.withIndex().associate { (index, id) -> id to standing(x = index.toDouble(), yaw = 0f) }

        assertEquals(emptyList(), SpectatorHandOrder.unsortedFromPositions(ids, standingTargets - ids.last()))
        assertEquals(
            emptyList(),
            SpectatorHandOrder.unsortedFromPositions(ids, standingTargets + (ids.last() to standingTargets.getValue(ids.last()).copy(poseDegrees = 90f))),
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
