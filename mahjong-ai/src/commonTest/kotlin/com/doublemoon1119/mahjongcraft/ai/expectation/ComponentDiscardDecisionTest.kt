package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.NeutralPositionRules
import com.doublemoon1119.mahjongcraft.logic.module.PositionRules
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.module.WinValue
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileComponents
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.toRiichiSnapshotForTests
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 從正式決策入口驗證同牌面不同特性的候選不會在估值前被合併。 */
class ComponentDiscardDecisionTest {
    /** 即使有價值的牌先出現在手中，AI 仍能選擇打出同牌面的普通牌。 */
    @Test
    fun `decision keeps valuable components when the valuable copy is first`() {
        val five = Tile.Numeric(Tile.Suit.Dot, 5)
        val red = IdentifiedTile(Uuid.random(), five, ComponentSet.EMPTY.with(RiichiTileComponents.RED_DORA, Unit))
        val ordinary = IdentifiedTile(Uuid.random(), five)
        val self = FakeMahjongPlayerFactory.create(hand = Hand(tiles = listOf(red, ordinary)))
        val table = FakeTableStateFactory.create(players = listOf(self, FakeMahjongPlayerFactory.create()), config = RiichiRuleConfig())
        val context = AiDecisionContext(
            snapshot = table.toRiichiSnapshotForTests(self.id),
            selfId = self.id,
            phase = AiDecisionPhase.OwnTurn,
            legalActions = emptyList(),
        )
        val module = object : MahjongRuleModule<RiichiRuleConfig> by ExpectationFixtures.module {
            override fun createShantenCalculator(): ShantenCalculator = object : ShantenCalculator {
                override fun calculate(hand: Hand): ShantenResult = ShantenResult.Tenpai(listOf(Tile.Honor.North))
            }

            override fun createPositionRules(): PositionRules = object : PositionRules by NeutralPositionRules {
                override fun winValue(
                    view: PositionView,
                    hand: Hand,
                    winningTile: TileValue,
                    isTsumo: Boolean,
                    declarations: Set<GameAction.Extension>,
                ): WinValue = WinValue.Points(if (hand.allTiles.any { it.components[RiichiTileComponents.RED_DORA] != null }) 1000 else 1)
            }
        }
        val level = InformationLevel.BEGINNER.copy(winValueDetail = 1.0)
        val decision = ExpectedValueEvaluator(level, ExpectationParameters.DEFAULT, module, NeutralOpponentModel(ReadingDepth.BASIC), context)
            .decide(ExpectationFixtures.extensionRegistry)

        assertEquals(GameCommand.Discard(ordinary.id), decision)
    }
}
