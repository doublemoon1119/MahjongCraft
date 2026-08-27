package com.doublemoon1119.mahjongcraft.platform.minecraft.animation

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 驗證中途胡牌收尾動畫的範圍規劃。
 *
 * 這裡最重要的一條是**副露絕對不能被蓋牌**：吃、碰、明槓、加槓、暗槓都是公開宣告，蓋成牌背既不合
 * 規則，也會摧毀既有的牌面、橫置方向與加槓疊牌版面。
 */
class WinPresentationCleanupPlanTest {
    /**
     * 贏家帶有副露時：立牌全部蓋起來，副露的牌一張都不進蓋牌集合——收尾動畫因此不會碰到那些 entity，
     * 它們維持原本的版面。
     */
    @Test
    fun `a winner holding melds gets only the standing tiles concealed`() {
        val standing = List(4) { tile(Tile.Numeric(Tile.Suit.Bamboo, it + 1)) }
        val drawn = tile(Tile.Honor.East)
        val chi = meld(MeldType.CHI, 3, RelativeDirection.Left)
        val pon = meld(MeldType.PON, 3, RelativeDirection.Across)
        val openKan = meld(MeldType.OPEN_KAN, 4, RelativeDirection.Right)
        val addedKan = meld(MeldType.ADDED_KAN, 4, RelativeDirection.Left)
        val closedKan = meld(MeldType.CLOSED_KAN, 4, RelativeDirection.Self)
        val hand = Hand(
            tiles = standing,
            melds = listOf(chi, pon, openKan, addedKan, closedKan),
            lastDrawn = drawn,
        )

        val plan = WinPresentationCleanupPlan.of(winnerHands = listOf(hand), showcaseHiddenTileIds = emptySet())

        assertEquals(
            (standing + drawn).mapTo(mutableSetOf()) { it.id },
            plan.concealTileIds,
            "Only standing tiles (including the drawn tile) may be concealed.",
        )
        val meldTileIds = hand.melds.flatMapTo(mutableSetOf()) { it.tiles.map { tile -> tile.id } }
        assertTrue(
            plan.animatedTileIds.none { it in meldTileIds },
            "Meld tiles must receive no cleanup animation at all, or their layout would be destroyed.",
        )
    }

    /**
     * 恢復可見只針對 showcase **實際**交接出去的那一組（可能包含留在放銃者牌河的榮和胡牌張），不是
     * 拿手牌全集去猜；而那張榮和胡牌張不屬於贏家立牌，因此不會被蓋牌。
     */
    @Test
    fun `visibility is restored for exactly the tiles the showcase borrowed`() {
        val standing = List(3) { tile(Tile.Numeric(Tile.Suit.Character, it + 1)) }
        val hand = Hand(tiles = standing, melds = listOf(meld(MeldType.PON, 3, RelativeDirection.Left)))
        val ronTileId = Uuid.random()
        val borrowed = standing.mapTo(mutableSetOf()) { it.id } + ronTileId

        val plan = WinPresentationCleanupPlan.of(winnerHands = listOf(hand), showcaseHiddenTileIds = borrowed)

        assertEquals(borrowed, plan.restoreVisibleTileIds)
        assertTrue(ronTileId !in plan.concealTileIds, "The ron tile stays in the discarder's river and is not concealed.")
    }

    /** 沒播 showcase（非役滿）時沒有任何牌被借走，就不該排入多餘的恢復可見動畫。 */
    @Test
    fun `an ordinary win restores nothing because nothing was hidden`() {
        val hand = Hand(tiles = listOf(tile(Tile.Honor.Red)))

        val plan = WinPresentationCleanupPlan.of(winnerHands = listOf(hand), showcaseHiddenTileIds = emptySet())

        assertEquals(emptySet(), plan.restoreVisibleTileIds)
        assertEquals(1, plan.concealTileIds.size)
    }

    /** 多家同時胡（頭跳以外的規則允許）時，每一位贏家的立牌都要蓋起來。 */
    @Test
    fun `every winner's standing tiles are concealed`() {
        val first = Hand(tiles = listOf(tile(Tile.Honor.South)))
        val second = Hand(tiles = listOf(tile(Tile.Honor.North)), melds = listOf(meld(MeldType.CHI, 3, RelativeDirection.Left)))

        val plan = WinPresentationCleanupPlan.of(winnerHands = listOf(first, second), showcaseHiddenTileIds = emptySet())

        assertEquals(
            setOf(first.tiles[0].id, second.tiles[0].id),
            plan.concealTileIds,
        )
    }

    private fun tile(face: Tile): IdentifiedTile = IdentifiedTile(id = Uuid.random(), tile = face)

    private fun meld(
        type: MeldType,
        size: Int,
        sourceDirection: RelativeDirection,
    ): Meld {
        val tiles = List(size) { tile(Tile.Honor.White) }
        return Meld(
            type = type,
            tiles = tiles,
            sourceTile = tiles.first().takeIf { sourceDirection != RelativeDirection.Self },
            sourceDirection = sourceDirection,
        )
    }
}
