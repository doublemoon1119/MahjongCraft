package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證沒有規則知識的對手模型保守且可預期。 */
class NeutralOpponentModelTest {
    private val self = FakeMahjongPlayerFactory.create(Wind.SOUTH)
    private val quiet = FakeMahjongPlayerFactory.create(Wind.EAST)
    private val active = FakeMahjongPlayerFactory.create(
        initialSeat = Wind.WEST,
        hand = Hand(
            melds = List(2) { index ->
                Meld(
                    type = MeldType.PON,
                    tiles = List(3) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, index + 1)) },
                    sourceDirection = RelativeDirection.Across,
                )
            },
        ),
        discardPile = (1..6).fold(FakeDiscardPile()) { pile, value ->
            pile.discardTile(FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, value)))
        },
    )
    private val model = NeutralOpponentModel(ReadingDepth.BASIC)
    private val view = PositionView(
        snapshot = FakeTableStateFactory.create(players = listOf(quiet, self, active)).toSnapshot(visibleHandPlayerIds = setOf(self.id)),
        evaluatorId = self.id,
    )

    /** 所有和牌與放銃以相同單位計算。 */
    @Test
    fun `win values are measured in a single unit`() {
        assertEquals(NeutralOpponentModel.UNIT_WIN_VALUE, model.baselineWinValue(view, self.id))
        assertEquals(NeutralOpponentModel.UNIT_WIN_VALUE, model.threat(view, active.id).expectedWinValue)
    }

    /** 捨牌危險度不區分牌張。 */
    @Test
    fun `discard danger does not distinguish tiles`() {
        assertEquals(
            model.discardDanger(view, active.id, Tile.Honor.Red),
            model.discardDanger(view, active.id, Tile.Numeric(Tile.Suit.Bamboo, 5)),
        )
    }

    /** 副露與捨牌越多，聽牌可能性越高。 */
    @Test
    fun `melds and discards raise the ready probability`() {
        assertTrue(model.threat(view, active.id).readyProbability > model.threat(view, quiet.id).readyProbability)
    }
}
