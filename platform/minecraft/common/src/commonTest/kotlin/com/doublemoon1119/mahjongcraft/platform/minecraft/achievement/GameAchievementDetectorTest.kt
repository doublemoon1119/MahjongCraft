package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 [GameAchievementDetector] 從已提交事實判定的通用成果與規則專屬成果分派。 */
class GameAchievementDetectorTest {
    private val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }

    /** 自摸的贏家取得胡牌與自摸。 */
    @Test
    fun `self-draw winner gets win and self-draw achievements`() {
        val game = fourPlayerGame()
        val (winner) = game.tableState.players

        val result = detector().detect(facts(game, HistoryFact.WinSettled(BuiltInRoundOutcomeIds.TSUMO, listOf(winDetails(winner.id)))))

        assertEquals(
            mapOf(winner.id to setOf(BuiltInAchievementIds.WIN, BuiltInAchievementIds.SELF_DRAW_WIN)),
            result.associate { it.playerId to it.achievementIds },
        )
    }

    /** 胡別人打出的牌時，贏家取得胡牌，放槍的人取得放槍。 */
    @Test
    fun `discard win credits the winner and the player who dealt in`() {
        val game = fourPlayerGame()
        val (winner, dealer) = game.tableState.players

        val result = detector().detect(
            facts(game, HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, listOf(winDetails(winner.id)), listOf(dealer.id))),
        )

        assertEquals(
            mapOf(
                winner.id to setOf(BuiltInAchievementIds.WIN, BuiltInAchievementIds.DISCARD_WIN),
                dealer.id to setOf(BuiltInAchievementIds.DEAL_IN),
            ),
            result.associate { it.playerId to it.achievementIds },
        )
    }

    /** 一張牌被兩家或三家同時胡時，放槍的人另外取得對應的成果。 */
    @Test
    fun `multiple winners on one discard credit the player who dealt in`() {
        val game = fourPlayerGame()
        val (dealer, first, second, third) = game.tableState.players

        val double = detector().detect(
            facts(game, HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, listOf(winDetails(first.id), winDetails(second.id)), listOf(dealer.id))),
        )
        val triple = detector().detect(
            facts(
                game,
                HistoryFact.WinSettled(
                    outcomeId = BuiltInRoundOutcomeIds.RON,
                    winDetails = listOf(winDetails(first.id), winDetails(second.id), winDetails(third.id)),
                    responsiblePlayerIds = listOf(dealer.id),
                ),
            ),
        )

        assertEquals(
            setOf(BuiltInAchievementIds.DEAL_IN, BuiltInAchievementIds.DOUBLE_DEAL_IN),
            double.single { it.playerId == dealer.id }.achievementIds,
        )
        assertEquals(
            setOf(BuiltInAchievementIds.DEAL_IN, BuiltInAchievementIds.TRIPLE_DEAL_IN),
            triple.single { it.playerId == dealer.id }.achievementIds,
        )
    }

    /** 牌山摸完的流局讓所有人取得流局；途中流局不算。 */
    @Test
    fun `only exhaustive draws count as draws`() {
        val game = fourPlayerGame()

        val exhaustive = detector().detect(facts(game, HistoryFact.RoundCompleted(roundSummary(game, RoundCompletionClassification.EXHAUSTIVE_DRAW))))
        val abortive = detector().detect(facts(game, HistoryFact.RoundCompleted(roundSummary(game, RoundCompletionClassification.ABORTIVE_DRAW))))

        assertEquals(game.tableState.players.map { it.id }.toSet(), exhaustive.map { it.playerId }.toSet())
        assertTrue(exhaustive.all { it.achievementIds == setOf(BuiltInAchievementIds.EXHAUSTIVE_DRAW) })
        assertTrue(abortive.isEmpty())
    }

    /** 終局時所有人取得完成對局，名次依規則排名，分數最高第一、最低最後。 */
    @Test
    fun `match completion ranks players with the rule ranking`() {
        val base = fourPlayerGame()
        val scores = listOf(25_000, 40_000, 10_000, 25_000)
        val players = base.tableState.players.mapIndexed { index, player -> player.copy(score = scores[index]) }
        val game = base.copy(tableState = base.tableState.copy(players = players))
        val (_, top, bottom) = players

        val result = detector()
            .detect(facts(game, HistoryFact.MatchCompleted("mahjongcraft:completed", players.associate { it.id to it.score })))
            .associate { it.playerId to it.achievementIds }

        assertTrue(result.values.all { BuiltInAchievementIds.MATCH_COMPLETED in it })
        assertEquals(listOf(top.id), result.filterValues { BuiltInAchievementIds.FIRST_PLACE in it }.keys.toList())
        assertEquals(listOf(bottom.id), result.filterValues { BuiltInAchievementIds.LAST_PLACE in it }.keys.toList())
    }

    /** AI 玩家不產生成果。 */
    @Test
    fun `ai players get no achievements`() {
        val players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { wind ->
            FakeMahjongPlayerFactory.create(initialSeat = wind, aiStrategyKey = if (wind == Wind.EAST) "mahjongcraft:beginner" else null)
        }
        val game = gameOf(players)
        val (aiWinner) = players

        val result = detector().detect(facts(game, HistoryFact.WinSettled(BuiltInRoundOutcomeIds.TSUMO, listOf(winDetails(aiWinner.id)))))

        assertTrue(result.isEmpty())
    }

    /** 規則專屬成果與通用成果併入同一組，只交給該對局規則登記的判定。 */
    @Test
    fun `rule achievements merge with generic achievements of the same transaction`() {
        val game = fourPlayerGame()
        val (winner) = game.tableState.players
        val registry = GameAchievementResolverRegistryImpl().apply {
            register(resolver(BuiltInRuleModuleIds.RIICHI) { mapOf(winner.id to setOf("example:rule_win")) })
            register(resolver(BuiltInRuleModuleIds.TAIWAN) { error("Only the game rule's resolver may run") })
        }

        val result = detector(registry).detect(facts(game, HistoryFact.WinSettled(BuiltInRoundOutcomeIds.TSUMO, listOf(winDetails(winner.id)))))

        assertEquals(
            setOf(BuiltInAchievementIds.WIN, BuiltInAchievementIds.SELF_DRAW_WIN, "example:rule_win"),
            result.single().achievementIds,
        )
        assertEquals(BuiltInRuleModuleIds.RIICHI, result.single().ruleModuleId)
        assertEquals(game.matchId, result.single().matchId)
    }

    private fun detector(registry: GameAchievementResolverRegistry = GameAchievementResolverRegistryImpl()) = GameAchievementDetector(
        moduleRegistry = moduleRegistry,
        resolverRegistry = registry,
    )

    private fun resolver(
        ruleModuleId: String,
        resolve: (CommittedGameFacts) -> Map<Uuid, Set<String>>,
    ): GameAchievementResolver = object : GameAchievementResolver {
        override val ruleModuleId: String = ruleModuleId

        override fun resolve(facts: CommittedGameFacts): Map<Uuid, Set<String>> = resolve(facts)
    }

    private fun fourPlayerGame(): Game = gameOf(listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { FakeMahjongPlayerFactory.create(initialSeat = it) })

    private fun gameOf(players: List<MahjongPlayer>): Game = Game(FakeTableStateFactory.create(players = players, config = RiichiRuleConfig()), GameFlowConfig())

    private fun facts(game: Game, fact: HistoryFact) = CommittedGameFacts(
        tableId = game.id,
        previousGame = game,
        game = game,
        facts = listOf(HistoryEventDraft(null, fact)),
    )

    private fun winDetails(playerId: Uuid) = HistoryWinDetails(playerId = playerId, detailFields = emptyList())

    private fun roundSummary(game: Game, classification: RoundCompletionClassification) = RoundCompletionSummary(
        outcomeId = "mahjongcraft:test_draw",
        classification = classification,
        beneficiaryPlayerIds = emptySet(),
        transitionDirective = RoundTransitionDirective.ADVANCE_DEALER,
        settledScoresByPlayerId = game.tableState.players.associate { it.id to it.score },
    )
}
