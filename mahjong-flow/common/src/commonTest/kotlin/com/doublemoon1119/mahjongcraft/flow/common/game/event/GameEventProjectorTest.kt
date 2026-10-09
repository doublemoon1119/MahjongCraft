package com.doublemoon1119.mahjongcraft.flow.common.game.event

import com.doublemoon1119.mahjongcraft.flow.api.event.MatchCompletion
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEndedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchStartedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettledEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettlementKind
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryActionResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** [GameEventProjector] 由每筆交易提交的事實產生對局事件，每次結算只產生一次。 */
class GameEventProjectorTest {
    private val east = Uuid.random()
    private val south = Uuid.random()
    private val west = Uuid.random()
    private val north = Uuid.random()
    private val registry = MahjongModuleRegistryImpl().apply {
        register(RiichiRuleConfig::class, RULE_MODULE_ID) { config, id -> RiichiRuleModule(id, config) }
    }
    private var eventCount = 0
    private val projector = GameEventProjector(registry) { Uuid.random().also { eventCount++ } }

    /** 對局開始時依座位列出玩家、是否由 AI 操控與起家座位。 */
    @Test
    fun `match start lists the players`() {
        val game = game(scores(25_000, 25_000, 25_000, 25_000), aiPlayerIds = setOf(south, north))

        val event = assertIs<MatchStartedEvent>(project(null, game, HistoryFact.MatchStarted(game.tableState, GameFlowConfig(), game.aiPlayerStrategyKeys)).single())

        assertEquals(game.matchId, event.matchId)
        assertEquals(game.id, event.venueId)
        assertEquals(RULE_MODULE_ID, event.ruleModuleId)
        assertEquals(listOf(east, south, west, north), event.players.map { it.playerId })
        assertEquals(listOf(false, true, false, true), event.players.map { it.isAi })
        assertEquals(listOf(0, 1, 2, 3), event.players.map { it.initialSeatIndex })
    }

    /** 一炮多響的同一筆交易只產生一次和牌結算，所有贏家都是得利者，前後分數與名次取交易前後的對局。 */
    @Test
    fun `a double ron settles once with every winner`() {
        val before = game(scores(25_000, 25_000, 25_000, 25_000))
        val after = before.withScores(scores(23_000, 25_000, 33_000, 19_000))

        val event = assertIs<RoundSettledEvent>(
            project(before, after, ron(west, responsible = north), ron(east, responsible = north)).single(),
        )

        assertEquals(RoundSettlementKind.WIN, event.kind)
        assertEquals(BuiltInRoundOutcomeIds.RON, event.outcomeId)
        assertEquals(setOf(west, east), event.beneficiaryPlayerIds)
        assertEquals(setOf(north), event.responsiblePlayerIds)
        assertEquals(listOf(east, south, west, north), event.players.map { it.playerId })
        assertEquals(listOf(25_000, 25_000, 25_000, 25_000), event.players.map { it.previousScore })
        assertEquals(listOf(23_000, 25_000, 33_000, 19_000), event.players.map { it.currentScore })
        assertEquals(listOf(1, 2, 3, 4), event.players.map { it.previousRank })
        assertEquals(listOf(3, 2, 1, 4), event.players.map { it.currentRank })
    }

    /** 規則的特殊結果在寫入本局結算摘要的交易產生一次結算。 */
    @Test
    fun `a special outcome settles from its round summary`() {
        val before = game(scores(25_000, 25_000, 25_000, 25_000))
        val after = before.withScores(scores(21_000, 37_000, 21_000, 21_000))
        val summary = summary(RiichiRoundOutcomeIds.NAGASHI_MANGAN, RoundCompletionClassification.WIN, after, beneficiaries = setOf(south))

        val event = assertIs<RoundSettledEvent>(project(before, after, HistoryFact.RuleEffectResolved(RiichiRoundOutcomeIds.NAGASHI_MANGAN, summary)).single())

        assertEquals(RoundSettlementKind.SPECIAL, event.kind)
        assertEquals(RiichiRoundOutcomeIds.NAGASHI_MANGAN, event.outcomeId)
        assertEquals(setOf(south), event.beneficiaryPlayerIds)
    }

    /** 規則在流局交易寫入本局結算摘要時，流局結算使用這份摘要。 */
    @Test
    fun `a draw with its summary uses the summary`() {
        val reason = RiichiExhaustiveDrawReason.Normal
        val before = game(scores(25_000, 25_000, 25_000, 25_000))
        val recorded = before.withScores(scores(26_500, 26_500, 23_500, 23_500)).recording(GameAction.ExhaustiveDraw(reason), east, south)
        val after = recorded.copy(roundCompletion = summary(reason.id, RoundCompletionClassification.EXHAUSTIVE_DRAW, recorded, beneficiaries = setOf(east, south)))

        val event = assertIs<RoundSettledEvent>(project(before, after, accepted(GameAction.ExhaustiveDraw(reason))).single())

        assertEquals(RoundSettlementKind.DRAW, event.kind)
        assertEquals(reason.id, event.outcomeId)
        assertEquals(setOf(east, south), event.beneficiaryPlayerIds)
        assertEquals(listOf(26_500, 26_500, 23_500, 23_500), event.players.map { it.currentScore })
    }

    /** 荒牌流局沒有人聽牌時，玩家的動作記錄不會新增流局，仍由同一筆交易寫入的本局結算摘要產生結算。 */
    @Test
    fun `a draw nobody is ready for settles from its summary`() {
        val reason = RiichiExhaustiveDrawReason.Normal
        val before = game(scores(25_000, 25_000, 25_000, 25_000))
        val after = before.copy(roundCompletion = summary(reason.id, RoundCompletionClassification.EXHAUSTIVE_DRAW, before, beneficiaries = emptySet()))

        val event = assertIs<RoundSettledEvent>(project(before, after, accepted(GameAction.ExhaustiveDraw(reason))).single())

        assertEquals(RoundSettlementKind.DRAW, event.kind)
        assertEquals(reason.id, event.outcomeId)
        assertEquals(emptySet(), event.beneficiaryPlayerIds)
    }

    /** 本局結算摘要晚於流局記錄才補上時，在記錄流局的交易依流局記錄產生結算；途中流局沒有得利者。 */
    @Test
    fun `an abortive draw without a summary settles from the recorded draws`() {
        val reason = RiichiExhaustiveDrawReason.KyuushuKyuuhai
        val before = game(scores(25_000, 25_000, 25_000, 25_000))
        val after = before.recording(GameAction.ExhaustiveDraw(reason), east, south, west, north)

        val event = assertIs<RoundSettledEvent>(project(before, after, accepted(GameAction.Discard(Uuid.random()))).single())

        assertEquals(RoundSettlementKind.DRAW, event.kind)
        assertEquals(reason.id, event.outcomeId)
        assertEquals(emptySet(), event.beneficiaryPlayerIds)
    }

    /** 換局交易的本局完成事實、和牌後判定本局去留的交易都不產生事件。 */
    @Test
    fun `round transitions and win continuations do not settle again`() {
        val reason = RiichiExhaustiveDrawReason.Normal
        val drawn = game(scores(25_000, 25_000, 25_000, 25_000)).recording(GameAction.ExhaustiveDraw(reason), east)
        val completed = drawn.copy(roundCompletion = summary(reason.id, RoundCompletionClassification.EXHAUSTIVE_DRAW, drawn, beneficiaries = setOf(east)))
        val nextRound = completed.copy(tableState = table(scores(25_000, 25_000, 25_000, 25_000), roundNumber = 2), roundCompletion = null)

        assertEquals(
            emptyList(),
            project(completed, nextRound, HistoryFact.RoundCompleted(checkNotNull(completed.roundCompletion)), HistoryFact.RoundStarted(nextRound.tableState)),
        )
        val won = game(scores(33_000, 25_000, 25_000, 17_000))
        val decided = won.copy(roundCompletion = summary(BuiltInRoundOutcomeIds.TSUMO, RoundCompletionClassification.WIN, won, beneficiaries = setOf(east)))
        assertEquals(emptyList(), project(won, decided, HistoryFact.WinContinuationResolved(WinRoundDirective.EndRound)))
        assertEquals(0, eventCount)
    }

    /** 正常打完時依規則的終局排名列出名次，同分時起家座位較前者名次較前。 */
    @Test
    fun `a completed match ranks the standings by the match ranking`() {
        val game = game(scores(30_000, 25_000, 25_000, 20_000)).let { it.copy(tableState = it.tableState.withSeatWinds(Wind.SOUTH, Wind.WEST, Wind.EAST, Wind.NORTH)) }

        val event = assertIs<MatchEndedEvent>(project(game, game, HistoryFact.MatchCompleted(REASON_ID, game.tableState.players.associate { it.id to it.score })).single())

        assertEquals(MatchCompletion.COMPLETED, event.completion)
        assertEquals(REASON_ID, event.reasonId)
        assertEquals(listOf(east, south, west, north), event.standings.map { it.playerId })
        assertEquals(listOf(1, 2, 3, 4), event.standings.map { it.rank })
        assertEquals(listOf(30_000, 25_000, 25_000, 20_000), event.standings.map { it.score })
    }

    /** 對局已被移除的交易不產生事件；每個事件各有一個事件 ID。 */
    @Test
    fun `removed games produce nothing and each event has its own id`() {
        val game = game(scores(25_000, 25_000, 25_000, 25_000))
        assertEquals(emptyList(), projector.project(CommittedGameFacts(game.id, game, null, listOf(HistoryEventDraft(null, HistoryFact.ReturnedToRoom)))))

        val after = game.withScores(scores(33_000, 25_000, 25_000, 17_000))
        val first = project(game, after, ron(east, responsible = north)).single()
        val second = project(game, after, ron(east, responsible = north)).single()
        assertEquals(2, setOf(first.eventId, second.eventId).size)
    }

    /** 事件中的集合是無法修改的獨立快照：經由轉型或迭代器修改都會失敗，也不影響權威的結算摘要。Java 呼叫修改方法的情況見 `MatchEventsJavaVisibilityTest`。 */
    @Test
    fun `event collections are independent read-only snapshots`() {
        val before = game(scores(25_000, 25_000, 25_000, 25_000))
        val after = before.withScores(scores(21_000, 37_000, 21_000, 21_000))
        val summary = summary(RiichiRoundOutcomeIds.NAGASHI_MANGAN, RoundCompletionClassification.WIN, after, beneficiaries = setOf(south))
        val settled = assertIs<RoundSettledEvent>(project(before, after, HistoryFact.RuleEffectResolved(RiichiRoundOutcomeIds.NAGASHI_MANGAN, summary)).single())
        val started = assertIs<MatchStartedEvent>(project(null, before, HistoryFact.MatchStarted(before.tableState, GameFlowConfig(), emptyMap())).single())
        val ended = assertIs<MatchEndedEvent>(project(after, after, HistoryFact.MatchCompleted(REASON_ID, emptyMap())).single())

        assertFails { (settled.beneficiaryPlayerIds as MutableSet<Uuid>).clear() }
        assertFails { (settled.beneficiaryPlayerIds.iterator() as MutableIterator<Uuid>).apply { next() }.remove() }
        assertFails { (settled.responsiblePlayerIds as MutableSet<Uuid>).add(south) }
        assertFails { (settled.players as MutableList<*>).clear() }
        assertFails { (started.players as MutableList<*>).removeAt(0) }
        assertFails { (ended.standings as MutableList<*>).clear() }
        assertEquals(setOf(south), summary.beneficiaryPlayerIds)
        assertEquals(setOf(south), settled.beneficiaryPlayerIds)
        assertEquals(4, settled.players.size)
    }

    /** 以一筆交易的前後對局與事實產生事件。 */
    private fun project(previous: Game?, game: Game, vararg facts: HistoryFact) = projector.project(CommittedGameFacts(game.id, previous, game, facts.map { HistoryEventDraft(null, it) }))

    /** 四名玩家依座位東、南、西、北的分數。 */
    private fun scores(vararg values: Int): Map<Uuid, Int> = listOf(east, south, west, north).zip(values.toList()).toMap()

    /** 以 [scores] 建立日麻的桌況。 */
    private fun table(scores: Map<Uuid, Int>, roundNumber: Int = 1): TableState = FakeTableStateFactory.create(
        id = VENUE_ID,
        players = listOf(east to Wind.EAST, south to Wind.SOUTH, west to Wind.WEST, north to Wind.NORTH).map { (id, wind) ->
            FakeMahjongPlayerFactory.create(id = id, initialSeat = wind).copy(score = scores.getValue(id))
        },
        config = RiichiRuleConfig(),
        roundNumber = roundNumber,
    )

    /** 以 [scores] 建立對局，[aiPlayerIds] 由 AI 操控。 */
    private fun game(scores: Map<Uuid, Int>, aiPlayerIds: Set<Uuid> = emptySet()): Game = Game(
        tableState = table(scores),
        flowConfig = GameFlowConfig(),
        aiPlayerStrategyKeys = aiPlayerIds.associateWith { "test:ai" },
        matchId = MATCH_ID,
    )

    /** 改變分數後的對局。 */
    private fun Game.withScores(scores: Map<Uuid, Int>): Game = copy(
        tableState = tableState.copy(players = tableState.players.map { it.copy(score = scores.getValue(it.id)) }),
    )

    /** [playerIds] 記錄 [action] 後的對局。 */
    private fun Game.recording(action: GameAction, vararg playerIds: Uuid): Game = copy(
        tableState = tableState.copy(players = tableState.players.map { if (it.id in playerIds) it.recordAction(action) else it }),
    )

    /** 依座位改變本局風位後的桌況。 */
    private fun TableState.withSeatWinds(vararg winds: Wind): TableState = copy(players = players.zip(winds.toList()).map { (player, wind) -> player.copy(seatWind = wind) })

    /** [winner] 榮和、[responsible] 放銃的事實。 */
    private fun ron(winner: Uuid, responsible: Uuid) = HistoryFact.WinSettled(
        outcomeId = BuiltInRoundOutcomeIds.RON,
        winDetails = listOf(HistoryWinDetails(playerId = winner, detailFields = emptyList())),
        responsiblePlayerIds = listOf(responsible),
    )

    /** 已接受 [action] 的事實。 */
    private fun accepted(action: GameAction) = HistoryFact.ActionAccepted(
        action = action,
        result = HistoryActionResult(
            affectedTileIds = emptyList(),
            newlyRevealedTileIds = emptyList(),
            remainingWallTileCount = 0,
            reservedWallTileIds = emptyList(),
            scoresByPlayerId = emptyMap(),
            nextPlayerId = east,
        ),
    )

    /** [game] 的本局結算摘要。 */
    private fun summary(
        outcomeId: String,
        classification: RoundCompletionClassification,
        game: Game,
        beneficiaries: Set<Uuid>,
    ) = RoundCompletionSummary(
        outcomeId = outcomeId,
        classification = classification,
        beneficiaryPlayerIds = beneficiaries,
        transitionDirective = RoundTransitionDirective.ADVANCE_DEALER,
        settledScoresByPlayerId = game.tableState.players.associate { it.id to it.score },
    )

    private companion object {
        /** 測試對局的規則模組 ID。 */
        const val RULE_MODULE_ID = "mahjongcraft:riichi"

        /** 測試的終局原因 ID。 */
        const val REASON_ID = "test:schedule_completed"

        /** 測試對局的場地 UUID。 */
        val VENUE_ID: Uuid = Uuid.random()

        /** 測試對局的場次 UUID。 */
        val MATCH_ID: Uuid = Uuid.random()
    }
}
