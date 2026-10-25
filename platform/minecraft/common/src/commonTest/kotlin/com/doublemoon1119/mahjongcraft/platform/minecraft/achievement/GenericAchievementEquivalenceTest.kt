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

/** 比較事件批次判定與凍結的事實判定，確認成果及七種統計完全一致。 */
class GenericAchievementEquivalenceTest {
    /** 供新舊判定共用的規則登記表。 */
    private val modules = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }

    /** 凍結判定所需的日麻排名規則。 */
    private val module = modules.getModule(RiichiRuleConfig())

    /** 對照各種和牌來源、責任分組、流局與終局的完整成果及統計集合。 */
    @Test
    fun `generic achievements remain equivalent across representative committed transactions`() {
        val game = game()
        val players = game.tableState.players
        val winner = players[0].id
        val second = players[1].id
        val dealer = players[2].id
        val cases = listOf(
            "tsumo" to committed(game, HistoryFact.WinSettled(BuiltInRoundOutcomeIds.TSUMO, listOf(win(winner)))),
            "ron" to committed(game, HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, listOf(win(winner)), listOf(dealer))),
            "explicit tsumo" to committedDrafts(game, HistoryEventDraft(winner, HistoryFact.ActionAccepted(GameAction.Tsumo, emptyResult(winner))), HistoryEventDraft(null, HistoryFact.WinSettled("example:win", listOf(win(winner))))),
            "explicit ron" to committedDrafts(game, HistoryEventDraft(winner, HistoryFact.ReactionResolved(GameAction.Ron(Uuid.random()), winner)), HistoryEventDraft(null, HistoryFact.WinSettled("example:win", listOf(win(winner)), listOf(dealer)))),
            "contradictory methods" to committedDrafts(game, HistoryEventDraft(winner, HistoryFact.ActionAccepted(GameAction.Tsumo, emptyResult(winner))), HistoryEventDraft(winner, HistoryFact.ReactionResolved(GameAction.Ron(Uuid.random()), winner)), HistoryEventDraft(null, HistoryFact.WinSettled("example:win", listOf(win(winner)), listOf(dealer)))),
            "two winners same responsibility" to committed(game, HistoryFact.ReactionResolved(GameAction.Ron(Uuid.random()), winner), HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, listOf(win(winner)), listOf(dealer)), HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, listOf(win(second)), listOf(dealer))),
            "two winners split responsibility" to committed(game, HistoryFact.ReactionResolved(GameAction.Ron(Uuid.random()), winner), HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, listOf(win(winner)), listOf(dealer)), HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, listOf(win(second)), listOf(players[3].id))),
            "zero winners with responsible player" to committed(game, HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, emptyList(), listOf(dealer))),
            "four winners no triple achievement" to committed(game, HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, listOf(win(winner)), listOf(dealer)), HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, listOf(win(second)), listOf(dealer)), HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, listOf(win(players[2].id)), listOf(dealer)), HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, listOf(win(players[3].id)), listOf(dealer))),
            "duplicate winner" to committed(game, HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, listOf(win(winner)), listOf(dealer)), HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, listOf(win(winner)), listOf(dealer))),
            "mixed builtin tsumo and custom" to committed(game, HistoryFact.WinSettled(BuiltInRoundOutcomeIds.TSUMO, listOf(win(winner))), HistoryFact.WinSettled("example:custom", listOf(win(second)))),
            "builtin tsumo and ron conflict" to committed(game, HistoryFact.WinSettled(BuiltInRoundOutcomeIds.TSUMO, listOf(win(winner))), HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, listOf(win(second)), listOf(dealer))),
            "rule effect win" to committed(game, HistoryFact.RuleEffectResolved("example:win", summary(game, RoundCompletionClassification.WIN), listOf(win(winner)))),
            "rule effect special" to committed(game, HistoryFact.RuleEffectResolved("example:special", summary(game, RoundCompletionClassification.EXTENSION), listOf(win(winner)))),
            "exhaustive round completion" to committed(game, HistoryFact.RoundCompleted(summary(game, RoundCompletionClassification.EXHAUSTIVE_DRAW))),
            "abortive round completion" to committed(game, HistoryFact.RoundCompleted(summary(game, RoundCompletionClassification.ABORTIVE_DRAW))),
            "normal match completion" to committed(game, HistoryFact.MatchCompleted("example:complete", players.associate { it.id to it.score })),
            "partial completion keys" to committed(game, HistoryFact.MatchCompleted("example:complete", mapOf(winner to players[0].score))),
            "empty completion keys" to committed(game, HistoryFact.MatchCompleted("example:complete", emptyMap())),
            "aborted match" to committed(game, HistoryFact.MatchAborted("example:aborted")),
            "no public events" to committed(game, HistoryFact.ReturnedToRoom),
        )
        cases.forEach { (label, facts) -> assertEquivalent(label, facts) }
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
        assertEquivalent("settlement before completion", settlementFacts)

        val completed = committed(settled, HistoryFact.RoundCompleted(summary))
        assertEquivalent("completed exhaustive draw", completed)
        val completedBatch = checkNotNull(GameEventProjector(modules).projectBatch(completed))
        assertEquals(
            game.tableState.players.associate { it.id to setOf(BuiltInAchievementIds.EXHAUSTIVE_DRAW) },
            GenericAchievementDetector.detect(completedBatch),
        )
    }

    /** 由同一提交分別執行舊判定與正式事件投影，逐人比對成果與統計。 */
    private fun assertEquivalent(label: String, facts: CommittedGameFacts) {
        val old = LegacyGenericAchievementDetector.detect(facts, module)
        val batch = GameEventProjector(modules).projectBatch(facts)
        val actual = batch?.let(GenericAchievementDetector::detect).orEmpty()
        assertEquals(old, actual, label)
        val players = (old.keys + actual.keys).distinct()
        players.forEach { playerId ->
            assertEquals(
                AchievementStatisticIds.statisticsFor(old[playerId].orEmpty()),
                AchievementStatisticIds.statisticsFor(actual[playerId].orEmpty()),
                "$label statistics for $playerId",
            )
        }
    }

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
