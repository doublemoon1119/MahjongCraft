package com.doublemoon1119.mahjongcraft.example.preparation

import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleModule
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
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

/** [ExamplePairingRoundPreparationResolver] 的最小行為測試。 */
class ExamplePairingRoundPreparationResolverTest {
    /** 受測的局前換牌流程解析器。 */
    private val resolver = ExamplePairingRoundPreparationResolver()

    /** 未啟用換牌時不應建立局前準備流程。 */
    @Test
    fun `disabled exchange does not create preparation`() {
        val config = ExamplePairingRuleConfig(exchangeEnabled = false)
        val tableState = tableState(config)
        val module = ExamplePairingRuleModule(ExamplePairingIds.RULE_MODULE, config)

        assertNull(resolver.roundPreparationConfig(config))
        assertNull(resolver.begin(tableState, module))
    }

    /** 啟用換牌時，每位玩家選出的兩張牌應交給下一位玩家。 */
    @Test
    fun `enabled exchange passes two selected tiles to the next seat`() {
        val config = ExamplePairingRuleConfig(exchangeEnabled = true)
        val tableState = tableState(config)
        val module = ExamplePairingRuleModule(ExamplePairingIds.RULE_MODULE, config)
        val preparation = assertNotNull(resolver.begin(tableState, module))
        val first = tableState.players[0].hand.standingTiles.take(2).map { it.id }.toSet()
        val second = tableState.players[1].hand.standingTiles.take(2).map { it.id }.toSet()
        val complete = preparation.copy(
            submissionsByPlayerId = mapOf(
                tableState.players[0].id to RoundPreparationSubmission.Tiles(first),
                tableState.players[1].id to RoundPreparationSubmission.Tiles(second),
            ),
        )

        assertEquals(30, resolver.roundPreparationConfig(config)?.baseSeconds)
        assertFalse(
            resolver.accepts(
                tableState,
                preparation,
                tableState.players[0].id,
                RoundPreparationSubmission.Tiles(first - first.first()),
                module,
            ),
        )
        assertFalse(
            resolver.accepts(
                tableState,
                preparation,
                tableState.players[0].id,
                RoundPreparationSubmission.Tiles(setOf(first.first(), Uuid.random())),
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
