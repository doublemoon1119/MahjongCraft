package com.doublemoon1119.mahjongcraft.example.decision

import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleModule
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** [ExamplePairingRuleDecisionResolver] 的最小行為測試。 */
class ExamplePairingRuleDecisionResolverTest {
    /** 受測的局前換牌流程解析器。 */
    private val resolver = ExamplePairingRuleDecisionResolver()

    /** 未啟用換牌時不應建立規則選擇流程。 */
    @Test
    fun `disabled exchange does not create rule decision`() {
        val config = ExamplePairingRuleConfig(exchangeEnabled = false)
        val tableState = tableState(config)
        val module = ExamplePairingRuleModule(ExamplePairingIds.RULE_MODULE, config)

        assertNull(resolver.ruleDecisionConfig(config))
        assertNull(resolver.begin(RuleDecisionTiming.ROUND_START, tableState, module))
    }

    /** 換牌只在開局進行，其他時間點不開啟選擇。 */
    @Test
    fun `exchange opens only at round start`() {
        val config = ExamplePairingRuleConfig(exchangeEnabled = true)
        val tableState = tableState(config)
        val module = ExamplePairingRuleModule(ExamplePairingIds.RULE_MODULE, config)

        RuleDecisionTiming.entries.filterNot { it == RuleDecisionTiming.ROUND_START }.forEach { timing ->
            assertNull(resolver.begin(timing, tableState, module), "$timing")
        }
    }

    /** 啟用換牌時，每位玩家選出的兩張牌應交給下一位玩家。 */
    @Test
    fun `enabled exchange passes two selected tiles to the next seat`() {
        val config = ExamplePairingRuleConfig(exchangeEnabled = true)
        val tableState = tableState(config)
        val module = ExamplePairingRuleModule(ExamplePairingIds.RULE_MODULE, config)
        val ruleDecision = PendingRuleDecision.open(
            step = assertNotNull(resolver.begin(RuleDecisionTiming.ROUND_START, tableState, module)),
            timing = RuleDecisionTiming.ROUND_START,
        )
        val first = tableState.players[0].hand.standingTiles.take(2).map { it.id }.toSet()
        val second = tableState.players[1].hand.standingTiles.take(2).map { it.id }.toSet()
        val complete = ruleDecision.copy(
            submissionsByPlayerId = mapOf(
                tableState.players[0].id to RuleDecisionSubmission.Tiles(first),
                tableState.players[1].id to RuleDecisionSubmission.Tiles(second),
            ),
        )

        assertEquals(30, resolver.ruleDecisionConfig(config)?.baseSeconds)
        assertFalse(
            resolver.accepts(
                tableState,
                ruleDecision,
                tableState.players[0].id,
                RuleDecisionSubmission.Tiles(first - first.first()),
                module,
            ),
        )
        assertFalse(
            resolver.accepts(
                tableState,
                ruleDecision,
                tableState.players[0].id,
                RuleDecisionSubmission.Tiles(setOf(first.first(), Uuid.random())),
                module,
            ),
        )
        val resolved = resolver.resolve(tableState, complete, module).tableState
        assertEquals(second, resolved.players[0].hand.standingTiles.takeLast(2).map { it.id }.toSet())
        assertEquals(first, resolved.players[1].hand.standingTiles.takeLast(2).map { it.id }.toSet())
        assertEquals(
            tableState.players.flatMap { it.hand.standingTiles }.map { it.id }.toSet(),
            resolved.players.flatMap { it.hand.standingTiles }.map { it.id }.toSet(),
        )
    }

    /**
     * 建立供解析器測試使用的兩人桌況。
     *
     * @param config 簡易配對規則設定。
     * @return 包含兩位玩家與測試手牌的桌況。
     */
    private fun tableState(config: ExamplePairingRuleConfig) = FakeTableStateFactory.create(
        config = config,
        players = listOf(
            FakeMahjongPlayerFactory.create(
                id = Uuid.random(),
                hand = Hand(List(3) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)) }),
            ),
            FakeMahjongPlayerFactory.create(
                id = Uuid.random(),
                hand = Hand(List(3) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)) }),
            ),
        ),
    )
}
