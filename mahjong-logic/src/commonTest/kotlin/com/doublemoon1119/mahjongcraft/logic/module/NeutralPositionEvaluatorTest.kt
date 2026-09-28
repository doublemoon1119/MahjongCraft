package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPositionEvaluator
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 驗證沒有規則知識的局面評估保守且可預期，以及規則模組如何提供評估。 */
class NeutralPositionEvaluatorTest {
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
    private val view = PositionView(
        snapshot = FakeTableStateFactory.create(players = listOf(quiet, self, active)).toSnapshot(visibleHandPlayerIds = setOf(self.id)),
        evaluatorId = self.id,
    )

    /** 和牌價值無法判斷，所有和牌與放銃以相同單位計算。 */
    @Test
    fun `win values are unknown and measured in a single unit`() {
        assertEquals(
            WinValue.Unknown,
            NeutralPositionEvaluator.winValue(view, Hand(), Tile.Honor.Red, isTsumo = true),
        )
        assertEquals(NeutralPositionEvaluator.UNIT_WIN_VALUE, NeutralPositionEvaluator.baselineWinValue(view, self.id))
        assertEquals(NeutralPositionEvaluator.UNIT_WIN_VALUE, NeutralPositionEvaluator.threat(view, active.id).expectedWinValue)
    }

    /** 捨牌危險度不區分牌張。 */
    @Test
    fun `discard danger does not distinguish tiles`() {
        assertEquals(
            NeutralPositionEvaluator.discardDanger(view, active.id, Tile.Honor.Red),
            NeutralPositionEvaluator.discardDanger(view, active.id, Tile.Numeric(Tile.Suit.Bamboo, 5)),
        )
    }

    /** 副露與捨牌越多，聽牌可能性越高。 */
    @Test
    fun `melds and discards raise the ready probability`() {
        assertTrue(NeutralPositionEvaluator.threat(view, active.id).readyProbability > NeutralPositionEvaluator.threat(view, quiet.id).readyProbability)
    }

    /** 宣告沒有任何效果。 */
    @Test
    fun `declarations have no effect`() {
        assertEquals(DeclarationEffect.NONE, NeutralPositionEvaluator.declarationEffect(view, GameAction.Extension(OtherAction)))
    }

    /** 沒有專屬評估的規則使用規則中立的評估，日麻使用自己的評估。 */
    @Test
    fun `rule modules provide their own evaluator or the neutral one`() {
        assertSame(NeutralPositionEvaluator, TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, TaiwanRuleConfig()).createPositionEvaluator())
        assertIs<RiichiPositionEvaluator>(RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, RiichiRuleConfig()).createPositionEvaluator())
    }

    /** 測試用的擴充動作。 */
    private object OtherAction : ExtensionGameAction {
        override val id: String = "example:other"
    }
}
