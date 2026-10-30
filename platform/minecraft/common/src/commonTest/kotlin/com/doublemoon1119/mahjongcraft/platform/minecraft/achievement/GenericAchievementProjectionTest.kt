package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettledEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettlementKind
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventProjector
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryActionResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 逐案驗證公開事件中的通用成果與七種統計，不使用其他判定實作產生答案。 */
class GenericAchievementProjectionTest {
    /** 供事件投影使用的規則登記表。 */
    private val modules = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }

    /** 驗證各種和牌來源、責任分組、流局與終局的成果及統計集合。 */
    @Test
    fun `generic achievements match explicit representative transaction outcomes`() {
        val game = game()
        val players = game.tableState.players
        val winner = players[0].id
        val second = players[1].id
        val dealer = players[2].id
        val baseWin = setOf(BuiltInAchievementIds.WIN)
        val tsumo = baseWin + BuiltInAchievementIds.SELF_DRAW_WIN
        val ron = baseWin + BuiltInAchievementIds.DISCARD_WIN
        val cases = listOf(
            ExpectedCase(
                label = "tsumo",
                facts = committed(
                    game,
                    HistoryFact.WinSettled(BuiltInRoundOutcomeIds.TSUMO, listOf(win(winner))),
                ),
                achievements = mapOf(winner to tsumo),
                statistics = mapOf(winner to setOf(AchievementStatisticIds.WINS, AchievementStatisticIds.SELF_DRAW_WINS)),
            ),
            ExpectedCase(
                label = "ron",
                facts = committed(
                    game,
                    HistoryFact.WinSettled(
                        BuiltInRoundOutcomeIds.RON,
                        listOf(win(winner)),
                        listOf(dealer),
                    ),
                ),
                achievements = mapOf(winner to ron, dealer to setOf(BuiltInAchievementIds.DEAL_IN)),
                statistics = mapOf(
                    winner to setOf(
                        AchievementStatisticIds.WINS,
                        AchievementStatisticIds.DISCARD_WINS,
                    ),
                    dealer to setOf(AchievementStatisticIds.DEAL_INS),
                ),
            ),
            ExpectedCase(
                label = "explicit tsumo",
                facts = committedDrafts(
                    game,
                    HistoryEventDraft(
                        winner,
                        HistoryFact.ActionAccepted(GameAction.Tsumo, emptyResult(winner)),
                    ),
                    HistoryEventDraft(
                        null,
                        HistoryFact.WinSettled("example:win", listOf(win(winner))),
                    ),
                ),
                achievements = mapOf(winner to tsumo),
                statistics = mapOf(winner to setOf(AchievementStatisticIds.WINS, AchievementStatisticIds.SELF_DRAW_WINS)),
            ),
            ExpectedCase(
                label = "explicit ron",
                facts = committedDrafts(
                    game,
                    HistoryEventDraft(
                        winner,
                        HistoryFact.ReactionResolved(GameAction.Ron(Uuid.random()), winner),
                    ),
                    HistoryEventDraft(
                        null,
                        HistoryFact.WinSettled("example:win", listOf(win(winner)), listOf(dealer)),
                    ),
                ),
                achievements = mapOf(winner to ron, dealer to setOf(BuiltInAchievementIds.DEAL_IN)),
                statistics = mapOf(
                    winner to setOf(
                        AchievementStatisticIds.WINS,
                        AchievementStatisticIds.DISCARD_WINS,
                    ),
                    dealer to setOf(AchievementStatisticIds.DEAL_INS),
                ),
            ),
            ExpectedCase(
                label = "contradictory methods",
                facts = committedDrafts(
                    game,
                    HistoryEventDraft(
                        winner,
                        HistoryFact.ActionAccepted(GameAction.Tsumo, emptyResult(winner)),
                    ),
                    HistoryEventDraft(
                        winner,
                        HistoryFact.ReactionResolved(GameAction.Ron(Uuid.random()), winner),
                    ),
                    HistoryEventDraft(
                        null,
                        HistoryFact.WinSettled("example:win", listOf(win(winner)), listOf(dealer)),
                    ),
                ),
                achievements = mapOf(winner to tsumo),
                statistics = mapOf(winner to setOf(AchievementStatisticIds.WINS, AchievementStatisticIds.SELF_DRAW_WINS)),
            ),
            ExpectedCase(
                label = "two winners same responsibility",
                facts = committed(
                    game,
                    HistoryFact.ReactionResolved(GameAction.Ron(Uuid.random()), winner),
                    HistoryFact.WinSettled(
                        BuiltInRoundOutcomeIds.RON,
                        listOf(win(winner)),
                        listOf(dealer),
                    ),
                    HistoryFact.WinSettled(
                        BuiltInRoundOutcomeIds.RON,
                        listOf(win(second)),
                        listOf(dealer),
                    ),
                ),
                achievements = mapOf(
                    winner to ron,
                    second to ron,
                    dealer to setOf(
                        BuiltInAchievementIds.DEAL_IN,
                        BuiltInAchievementIds.DOUBLE_DEAL_IN,
                    ),
                ),
                statistics = mapOf(
                    winner to setOf(
                        AchievementStatisticIds.WINS,
                        AchievementStatisticIds.DISCARD_WINS,
                    ),
                    second to setOf(
                        AchievementStatisticIds.WINS,
                        AchievementStatisticIds.DISCARD_WINS,
                    ),
                    dealer to setOf(AchievementStatisticIds.DEAL_INS),
                ),
            ),
            ExpectedCase(
                label = "two winners split responsibility",
                facts = committed(
                    game,
                    HistoryFact.ReactionResolved(GameAction.Ron(Uuid.random()), winner),
                    HistoryFact.WinSettled(
                        BuiltInRoundOutcomeIds.RON,
                        listOf(win(winner)),
                        listOf(dealer),
                    ),
                    HistoryFact.WinSettled(
                        BuiltInRoundOutcomeIds.RON,
                        listOf(win(second)),
                        listOf(players[3].id),
                    ),
                ),
                achievements = mapOf(
                    winner to ron,
                    second to ron,
                    dealer to setOf(BuiltInAchievementIds.DEAL_IN),
                    players[3].id to setOf(BuiltInAchievementIds.DEAL_IN),
                ),
                statistics = mapOf(
                    winner to setOf(
                        AchievementStatisticIds.WINS,
                        AchievementStatisticIds.DISCARD_WINS,
                    ),
                    second to setOf(
                        AchievementStatisticIds.WINS,
                        AchievementStatisticIds.DISCARD_WINS,
                    ),
                    dealer to setOf(AchievementStatisticIds.DEAL_INS),
                    players[3].id to setOf(AchievementStatisticIds.DEAL_INS),
                ),
            ),
            ExpectedCase(
                label = "zero winners with responsible player",
                facts = committed(
                    game,
                    HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, emptyList(), listOf(dealer)),
                ),
                achievements = mapOf(dealer to setOf(BuiltInAchievementIds.DEAL_IN)),
                statistics = mapOf(dealer to setOf(AchievementStatisticIds.DEAL_INS)),
            ),
            ExpectedCase(
                label = "four winners no triple achievement",
                facts = committed(
                    game,
                    HistoryFact.WinSettled(
                        BuiltInRoundOutcomeIds.RON,
                        listOf(win(winner)),
                        listOf(dealer),
                    ),
                    HistoryFact.WinSettled(
                        BuiltInRoundOutcomeIds.RON,
                        listOf(win(second)),
                        listOf(dealer),
                    ),
                    HistoryFact.WinSettled(
                        BuiltInRoundOutcomeIds.RON,
                        listOf(win(players[2].id)),
                        listOf(dealer),
                    ),
                    HistoryFact.WinSettled(
                        BuiltInRoundOutcomeIds.RON,
                        listOf(win(players[3].id)),
                        listOf(dealer),
                    ),
                ),
                achievements = players.associate { it.id to ron } + (dealer to ron + BuiltInAchievementIds.DEAL_IN),
                statistics = players.associate {
                    it.id to setOf(
                        AchievementStatisticIds.WINS,
                        AchievementStatisticIds.DISCARD_WINS,
                    )
                } + (dealer to setOf(AchievementStatisticIds.WINS, AchievementStatisticIds.DISCARD_WINS, AchievementStatisticIds.DEAL_INS)),
            ),
            ExpectedCase(
                label = "duplicate winner",
                facts = committed(
                    game,
                    HistoryFact.WinSettled(
                        BuiltInRoundOutcomeIds.RON,
                        listOf(win(winner)),
                        listOf(dealer),
                    ),
                    HistoryFact.WinSettled(
                        BuiltInRoundOutcomeIds.RON,
                        listOf(win(winner)),
                        listOf(dealer),
                    ),
                ),
                achievements = mapOf(winner to ron, dealer to setOf(BuiltInAchievementIds.DEAL_IN)),
                statistics = mapOf(
                    winner to setOf(
                        AchievementStatisticIds.WINS,
                        AchievementStatisticIds.DISCARD_WINS,
                    ),
                    dealer to setOf(AchievementStatisticIds.DEAL_INS),
                ),
            ),
            ExpectedCase(
                label = "mixed builtin tsumo and custom",
                facts = committed(
                    game,
                    HistoryFact.WinSettled(BuiltInRoundOutcomeIds.TSUMO, listOf(win(winner))),
                    HistoryFact.WinSettled("example:custom", listOf(win(second))),
                ),
                achievements = mapOf(winner to tsumo, second to tsumo),
                statistics = mapOf(
                    winner to setOf(
                        AchievementStatisticIds.WINS,
                        AchievementStatisticIds.SELF_DRAW_WINS,
                    ),
                    second to setOf(
                        AchievementStatisticIds.WINS,
                        AchievementStatisticIds.SELF_DRAW_WINS,
                    ),
                ),
            ),
            ExpectedCase(
                label = "builtin tsumo and ron conflict",
                facts = committed(
                    game,
                    HistoryFact.WinSettled(BuiltInRoundOutcomeIds.TSUMO, listOf(win(winner))),
                    HistoryFact.WinSettled(
                        BuiltInRoundOutcomeIds.RON,
                        listOf(win(second)),
                        listOf(dealer),
                    ),
                ),
                achievements = mapOf(winner to baseWin, second to baseWin),
                statistics = mapOf(
                    winner to setOf(AchievementStatisticIds.WINS),
                    second to setOf(AchievementStatisticIds.WINS),
                ),
            ),
            ExpectedCase(
                label = "rule effect win",
                facts = committed(
                    game,
                    HistoryFact.RuleEffectResolved(
                        "example:win",
                        summary(game, RoundCompletionClassification.WIN),
                        listOf(win(winner)),
                    ),
                ),
                achievements = mapOf(winner to baseWin),
                statistics = mapOf(winner to setOf(AchievementStatisticIds.WINS)),
            ),
            ExpectedCase(
                label = "rule effect special",
                facts = committed(
                    game,
                    HistoryFact.RuleEffectResolved(
                        "example:special",
                        summary(game, RoundCompletionClassification.EXTENSION),
                        listOf(win(winner)),
                    ),
                ),
                achievements = emptyMap(),
                statistics = emptyMap(),
            ),
            ExpectedCase(
                label = "exhaustive round completion",
                facts = committed(
                    game,
                    HistoryFact.RoundCompleted(summary(game, RoundCompletionClassification.EXHAUSTIVE_DRAW)),
                ),
                achievements = players.associate { it.id to setOf(BuiltInAchievementIds.EXHAUSTIVE_DRAW) },
                statistics = players.associate { it.id to setOf(AchievementStatisticIds.EXHAUSTIVE_DRAWS) },
            ),
            ExpectedCase(
                label = "abortive round completion",
                facts = committed(
                    game,
                    HistoryFact.RoundCompleted(summary(game, RoundCompletionClassification.ABORTIVE_DRAW)),
                ),
                achievements = emptyMap(),
                statistics = emptyMap(),
            ),
            ExpectedCase(
                label = "normal match completion",
                facts = committed(
                    game,
                    HistoryFact.MatchCompleted(
                        "example:complete",
                        players.associate { it.id to it.score },
                    ),
                ),
                achievements = players.associate { it.id to setOf(BuiltInAchievementIds.MATCH_COMPLETED) } + (winner to setOf(BuiltInAchievementIds.MATCH_COMPLETED, BuiltInAchievementIds.FIRST_PLACE)) + (players[3].id to setOf(BuiltInAchievementIds.MATCH_COMPLETED, BuiltInAchievementIds.LAST_PLACE)),
                statistics = players.associate { it.id to setOf(AchievementStatisticIds.MATCHES_COMPLETED) } + (winner to setOf(AchievementStatisticIds.MATCHES_COMPLETED, AchievementStatisticIds.FIRST_PLACES)) + (players[3].id to setOf(AchievementStatisticIds.MATCHES_COMPLETED)),
            ),
            ExpectedCase(
                label = "partial completion keys",
                facts = committed(
                    game,
                    HistoryFact.MatchCompleted("example:complete", mapOf(winner to players[0].score)),
                ),
                achievements = mapOf(
                    winner to setOf(
                        BuiltInAchievementIds.MATCH_COMPLETED,
                        BuiltInAchievementIds.FIRST_PLACE,
                    ),
                    players[3].id to setOf(BuiltInAchievementIds.LAST_PLACE),
                ),
                statistics = mapOf(winner to setOf(AchievementStatisticIds.MATCHES_COMPLETED, AchievementStatisticIds.FIRST_PLACES)),
            ),
            ExpectedCase(
                label = "empty completion keys",
                facts = committed(game, HistoryFact.MatchCompleted("example:complete", emptyMap())),
                achievements = mapOf(
                    winner to setOf(BuiltInAchievementIds.FIRST_PLACE),
                    players[3].id to setOf(BuiltInAchievementIds.LAST_PLACE),
                ),
                statistics = mapOf(winner to setOf(AchievementStatisticIds.FIRST_PLACES)),
            ),
            ExpectedCase(
                label = "aborted match",
                facts = committed(game, HistoryFact.MatchAborted("example:aborted")),
                achievements = emptyMap(),
                statistics = emptyMap(),
            ),
            ExpectedCase(
                label = "no public events",
                facts = committed(game, HistoryFact.ReturnedToRoom),
                achievements = emptyMap(),
                statistics = emptyMap(),
            ),
        )
        cases.forEach { assertProjected(it) }
    }

    /** 荒牌結算不能提早累加次數，完成交易才授予一次流局成果。 */
    @Test
    fun `draw settlement and round completion remain separate achievement transactions`() {
        val game = game()
        val summary = summary(game, RoundCompletionClassification.EXHAUSTIVE_DRAW)
        val settled = game.copy(roundCompletion = summary)
        val settlementFacts = CommittedGameFacts(
            venueId = game.id,
            previousGame = game,
            game = settled,
            facts = listOf(HistoryEventDraft(game.tableState.players.first().id, HistoryFact.ActionAccepted(GameAction.Draw, emptyResult(game.tableState.players.first().id)))),
        )
        val batch = checkNotNull(GameEventProjector(modules).projectBatch(settlementFacts))
        assertEquals(RoundSettlementKind.DRAW, batch.events.filterIsInstance<RoundSettledEvent>().single().kind)
        assertTrue(GenericAchievementDetector.detect(batch).isEmpty())
        assertProjected(ExpectedCase("settlement before completion", settlementFacts, emptyMap(), emptyMap()))

        val completed = committed(settled, HistoryFact.RoundCompleted(summary))
        assertProjected(ExpectedCase("completed exhaustive draw", completed, game.tableState.players.associate { it.id to setOf(BuiltInAchievementIds.EXHAUSTIVE_DRAW) }, game.tableState.players.associate { it.id to setOf(AchievementStatisticIds.EXHAUSTIVE_DRAWS) }))
        val completedBatch = checkNotNull(GameEventProjector(modules).projectBatch(completed))
        assertEquals(
            game.tableState.players.associate { it.id to setOf(BuiltInAchievementIds.EXHAUSTIVE_DRAW) },
            GenericAchievementDetector.detect(completedBatch),
        )
    }

    /** 依固定測試案例的預期成果與統計驗證事件投影。 */
    private fun assertProjected(case: ExpectedCase) {
        val batch = GameEventProjector(modules).projectBatch(case.facts)
        val actual = batch?.let(GenericAchievementDetector::detect).orEmpty()
        assertEquals(case.achievements, actual, case.label)
        val players = (case.statistics.keys + actual.keys).distinct()
        players.forEach { playerId ->
            assertEquals(
                case.statistics[playerId].orEmpty(),
                AchievementStatisticIds.statisticsFor(actual[playerId].orEmpty()),
                "${case.label} statistics for $playerId",
            )
        }
    }

    /**
     * 一個事件交易的固定成果與統計預期。
     *
     * @property label 測試案例名稱。
     * @property facts 產生事件批次的提交資料。
     * @property achievements 每位玩家應取得的成果。
     * @property statistics 每位玩家應累加的統計。
     */
    private data class ExpectedCase(
        val label: String,
        val facts: CommittedGameFacts,
        val achievements: Map<Uuid, Set<String>>,
        val statistics: Map<Uuid, Set<String>>,
    )

    /** 建立四名真人參與的日麻對局。 */
    private fun game(): Game = Game(
        FakeTableStateFactory.create(
            players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { FakeMahjongPlayerFactory.create(initialSeat = it) },
            config = RiichiRuleConfig(),
        ),
        GameFlowConfig(),
    )

    /** 建立不指定動作者的提交，適用於沒有動作來源的結果。 */
    private fun committed(game: Game, vararg facts: HistoryFact): CommittedGameFacts = CommittedGameFacts(
        venueId = game.id,
        previousGame = game,
        game = game,
        facts = facts.map { HistoryEventDraft(null, it) },
    )

    /** 保留明確動作者，驗證自摸與榮和來源互相矛盾時的判定。 */
    private fun committedDrafts(game: Game, vararg drafts: HistoryEventDraft): CommittedGameFacts = CommittedGameFacts(
        venueId = game.id,
        previousGame = game,
        game = game,
        facts = drafts.toList(),
    )

    /** 建立只含贏家識別碼的和牌明細。 */
    private fun win(playerId: Uuid) = HistoryWinDetails(playerId, emptyList())

    /** 建立指定完成分類的本局摘要。 */
    private fun summary(game: Game, classification: RoundCompletionClassification) = RoundCompletionSummary(
        outcomeId = "example:outcome",
        classification = classification,
        beneficiaryPlayerIds = emptySet(),
        transitionDirective = RoundTransitionDirective.PassToNextSeat,
        settledScoresByPlayerId = game.tableState.players.associate { it.id to it.score },
    )

    /** 建立自摸動作的最小結果，動作者另由事實草稿明確指定。 */
    private fun emptyResult(actor: Uuid) = HistoryActionResult(
        affectedTileIds = emptyList(),
        newlyRevealedTileIds = emptyList(),
        remainingWallTileCount = 0,
        reservedWallTileIds = emptyList(),
        scoresByPlayerId = emptyMap(),
        nextPlayerId = actor,
    )
}
