package com.doublemoon1119.mahjongcraft.flow.server.game.simulation

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.BuiltInMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** 驗證固定日麻榮和情境與整場模擬終局檢查器的邊界條件。 */
class RiichiSimulationAssertionsTest {
    /** 固定一局大三元榮和，確認合法擊飛會在第一局結束且不被誤判為模擬失敗。 */
    @Test
    fun `fixed ron win may legally end an east match by bust`() = runTest {
        val runtime = SimulationRuntime(defaultStrategyKey = RON_STRATEGY_KEY)
        runtime.aiStrategyRegistry.register(RON_STRATEGY_KEY) { RonOnlyStrategy() }

        val gameId = Uuid.random()
        val eastId = Uuid.random()
        val southId = Uuid.random()
        val westId = Uuid.random()
        val northId = Uuid.random()
        val config = RiichiRuleConfig(gameLength = RiichiGameLength.East)
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val players = listOf(
            FakeMahjongPlayerFactory.create(
                initialSeat = Wind.EAST,
                id = eastId,
                discardPile = FakeDiscardPile().discardTile(discardedTile),
            ).copy(score = INITIAL_SCORE),
            FakeMahjongPlayerFactory.create(
                initialSeat = Wind.SOUTH,
                id = southId,
                hand = Hand(daisangenTiles().map(FakeIdentifiedTileFactory::create)),
                discardPile = FakeDiscardPile().discardTile(
                    FakeIdentifiedTileFactory.create(Tile.Honor.South),
                ),
            ).copy(score = INITIAL_SCORE),
            FakeMahjongPlayerFactory.create(
                initialSeat = Wind.WEST,
                id = westId,
            ).copy(score = INITIAL_SCORE),
            FakeMahjongPlayerFactory.create(
                initialSeat = Wind.NORTH,
                id = northId,
            ).copy(score = INITIAL_SCORE),
        )
        runtime.gameRepository.setTableState(
            FakeTableStateFactory.create(
                id = gameId,
                players = players,
                dealerPlayerId = eastId,
                config = config,
                currentPlayerIndex = 0,
                pendingReaction = PendingReaction(eastId, discardedTile.id, setOf(southId)),
            ),
            players.associate { it.id to RON_STRATEGY_KEY },
        )

        runtime.coordinator.driveAutomatedPlayers(gameId)

        val game = requireNotNull(runtime.gameRepository.getGame(gameId))
        val finalState = game.tableState
        assertEquals(BuiltInMatchEndReasonIds.PLAYER_BUSTED, game.matchEndReasonId)
        assertEquals(PendingGameTransition.ReturnToRoom, game.pendingTransition)
        assertEquals(1, runtime.gameRepository.rounds(gameId).size)
        assertEquals(listOf(-7_000, 57_000, INITIAL_SCORE, INITIAL_SCORE), finalState.players.map { it.score })
        assertEquals(100_000, finalState.players.sumOf { it.score })
        assertLegalRiichiMatchCompletion(
            config = config,
            roundCount = runtime.gameRepository.rounds(gameId).size,
            matchEndReasonId = game.matchEndReasonId,
            finalScoresByPlayer = finalState.players.associate { it.id to it.score },
        )
    }

    /** 缺少終局原因時，驗證器必須拒絕沒有足夠資訊的模擬結果。 */
    @Test
    fun `completion assertion rejects missing reason`() {
        assertFailsWith<AssertionError> {
            assertLegalRiichiMatchCompletion(
                config = eastConfig(),
                roundCount = 4,
                matchEndReasonId = null,
                finalScoresByPlayer = initialScores(),
            )
        }
    }

    /** 未知終局原因不應被當成合法日麻終局。 */
    @Test
    fun `completion assertion rejects unknown reason`() {
        assertFailsWith<AssertionError> {
            assertLegalRiichiMatchCompletion(
                config = eastConfig(),
                roundCount = 4,
                matchEndReasonId = "test:unknown_reason",
                finalScoresByPlayer = initialScores(),
            )
        }
    }

    /** 標示擊飛但沒有低於門檻的玩家時，驗證器必須拒絕自相矛盾的結果。 */
    @Test
    fun `completion assertion rejects bust reason without a busted score`() {
        assertFailsWith<AssertionError> {
            assertLegalRiichiMatchCompletion(
                config = eastConfig(),
                roundCount = 1,
                matchEndReasonId = BuiltInMatchEndReasonIds.PLAYER_BUSTED,
                finalScoresByPlayer = initialScores(),
            )
        }
    }

    /** 分數恰好等於擊飛門檻時不算低於門檻，驗證器必須拒絕這種擊飛標記。 */
    @Test
    fun `completion assertion rejects score equal to bust threshold`() {
        assertFailsWith<AssertionError> {
            assertLegalRiichiMatchCompletion(
                config = eastConfig(),
                roundCount = 1,
                matchEndReasonId = BuiltInMatchEndReasonIds.PLAYER_BUSTED,
                finalScoresByPlayer = initialScores().values
                    .mapIndexed { index, score -> Uuid.random() to if (index == 0) 0 else score }
                    .toMap(),
            )
        }
    }

    /** 非擊飛的整場原因若尚未完成最低局數，驗證器必須拒絕過早終局。 */
    @Test
    fun `completion assertion rejects non-bust reason before minimum rounds`() {
        assertFailsWith<AssertionError> {
            assertLegalRiichiMatchCompletion(
                config = eastConfig(),
                roundCount = 1,
                matchEndReasonId = BuiltInMatchEndReasonIds.SCHEDULE_COMPLETED,
                finalScoresByPlayer = initialScores(),
            )
        }
    }

    /** 四局後的正常東風戰終局應通過驗證器。 */
    @Test
    fun `completion assertion accepts a legal scheduled ending`() {
        assertLegalRiichiMatchCompletion(
            config = eastConfig(),
            roundCount = 4,
            matchEndReasonId = BuiltInMatchEndReasonIds.SCHEDULE_COMPLETED,
            finalScoresByPlayer = initialScores(),
        )
    }

    /** 建立測試用東風戰設定。 */
    private fun eastConfig(): RiichiRuleConfig = RiichiRuleConfig(gameLength = RiichiGameLength.East)

    /** 建立四家合計十萬點的初始分數。 */
    private fun initialScores(): Map<Uuid, Int> = List(4) { Uuid.random() }.associateWith { INITIAL_SCORE }

    /** 固定可由一張紅中完成的大三元十三張立牌。 */
    private fun daisangenTiles() = listOf(
        Tile.Honor.Red,
        Tile.Honor.Red,
        Tile.Honor.Green,
        Tile.Honor.Green,
        Tile.Honor.Green,
        Tile.Honor.White,
        Tile.Honor.White,
        Tile.Honor.White,
        Tile.Numeric(Tile.Suit.Character, 1),
        Tile.Numeric(Tile.Suit.Character, 2),
        Tile.Numeric(Tile.Suit.Character, 3),
        Tile.Numeric(Tile.Suit.Dot, 5),
        Tile.Numeric(Tile.Suit.Dot, 5),
    )

    /** 只在南家收到合法榮和動作時回應的固定 AI。 */
    private class RonOnlyStrategy : MahjongAiStrategy {
        override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand = when (context.phase) {
            AiDecisionPhase.RespondingToDiscard -> GameCommand.RespondToDiscard(
                context.legalActions.filterIsInstance<GameAction.Ron>().single(),
            )
            AiDecisionPhase.RespondingToRobbing -> GameCommand.RespondToRobbing(GameAction.Pass)
            AiDecisionPhase.OwnTurn -> error("The fixed ron scenario should end before an own turn")
        }
    }

    private companion object {
        /** 固定測試策略的 registry key。 */
        const val RON_STRATEGY_KEY = "test:ron"

        /** 日麻預設每位玩家的起始分數。 */
        const val INITIAL_SCORE = 25_000
    }
}
