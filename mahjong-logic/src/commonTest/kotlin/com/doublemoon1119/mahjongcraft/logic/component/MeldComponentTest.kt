package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** 驗證副露身分及其組件在加槓時持續保留。 */
class MeldComponentTest {
    /** 加槓更新原組，不建立新的副露身分或丟失組件。 */
    @Test
    fun `added kan retains meld identity and components`() {
        val marker = ComponentType(
            id = "example:meld/score",
            valueClass = Int::class,
            copyValue = { it },
            target = BuiltInComponentOwners.MELD,
        )
        val tiles = List(4) { IdentifiedTile(Uuid.random(), Tile.Numeric(Tile.Suit.Dot, 5)) }
        val original = Meld(
            type = MeldType.PON,
            tiles = tiles.take(3),
            sourceTile = tiles.first(),
            sourceDirection = RelativeDirection.Left,
            publiclyRevealedTileIds = tiles.take(3).mapTo(mutableSetOf()) { it.id },
            faceDownTileIds = emptySet(),
            components = ComponentSet.EMPTY.with(marker, 4),
        )
        val hand = Hand(
            melds = listOf(original),
            lastDrawn = tiles.last(),
        )
        val upgraded = hand.upgradeToAddedKan(tiles.last(), 0).melds.single()
        assertEquals(original.id, upgraded.id)
        assertEquals(4, upgraded.components[marker])
        assertEquals(MeldType.ADDED_KAN, upgraded.type)
        assertEquals(tiles, upgraded.tiles)
        assertEquals(original.sourceTile, upgraded.sourceTile)
        assertEquals(MeldType.PON, original.type)
    }

    /** 多組副露時加槓第二組，每組的身分與組件仍各自對應，不因位置或種類改變而錯置。 */
    @Test
    fun `added kan on a later meld keeps every meld's own data`() {
        val marker = ComponentType("example:meld/order", Int::class, { it }, target = BuiltInComponentOwners.MELD)
        fun pon(tile: Tile, value: Int): Pair<Meld, IdentifiedTile> {
            val tiles = List(4) { IdentifiedTile(Uuid.random(), tile) }
            val meld = Meld(
                type = MeldType.PON,
                tiles = tiles.take(3),
                sourceTile = tiles.first(),
                sourceDirection = RelativeDirection.Across,
                publiclyRevealedTileIds = tiles.take(3).mapTo(mutableSetOf()) { it.id },
                faceDownTileIds = emptySet(),
                components = ComponentSet.EMPTY.with(marker, value),
            )
            return meld to tiles.last()
        }
        val (first, _) = pon(Tile.Honor.Red, 1)
        val (second, fourth) = pon(Tile.Honor.Green, 2)
        val hand = Hand(tiles = listOf(fourth), melds = listOf(first, second))

        val melds = hand.upgradeToAddedKan(fourth, 1).melds

        assertEquals(listOf(first.id, second.id), melds.map { it.id })
        assertEquals(listOf(1, 2), melds.map { it.components[marker] })
        assertEquals(listOf(MeldType.PON, MeldType.ADDED_KAN), melds.map { it.type })
    }

    /** 不能將屬於牌張的組件誤放在整組副露。 */
    @Test
    fun `meld rejects components for another owner`() {
        val marker = ComponentType(
            id = "example:tile/score",
            valueClass = Int::class,
            copyValue = { it },
            target = BuiltInComponentOwners.TILE,
        )
        assertFailsWith<IllegalArgumentException> {
            Meld(
                type = MeldType.CLOSED_KAN,
                tiles = emptyList(),
                sourceDirection = RelativeDirection.Self,
                publiclyRevealedTileIds = emptySet(),
                faceDownTileIds = emptySet(),
                components = ComponentSet.EMPTY.with(marker, 1),
            )
        }
    }
}
