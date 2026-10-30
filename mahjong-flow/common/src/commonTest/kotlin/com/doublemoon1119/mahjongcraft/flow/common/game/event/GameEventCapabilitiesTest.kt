package com.doublemoon1119.mahjongcraft.flow.common.game.event

import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEndedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundCompletedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettledEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.WinMethod
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryActionResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailEntry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.uuid.Uuid

/** 驗證公開事件新增能力，不透過成就消費端猜測投影是否正確。 */
class GameEventCapabilitiesTest {
    /** 這組測試使用的來源對局。 */
    private val game = Game(FakeTableStateFactory.create(config = RiichiRuleConfig()), GameFlowConfig())

    /** 贏家識別碼。 */
    private val winner = game.tableState.players.first().id

    /** 另一位玩家識別碼。 */
    private val other = game.tableState.players.last().id

    /** 註冊測試所需的規則工廠。 */
    private val modules = MahjongModuleRegistryImpl().apply {
        register(MahjongModuleDefinition(RiichiRuleConfig::class, "mahjongcraft:riichi") { config, id -> RiichiRuleModule(id, config) })
    }

    /** 自訂結果沿用同筆明確自摸來源。 */
    @Test
    fun `custom outcome with explicit tsumo carries self draw details`() {
        val batch = assertNotNull(projector().projectBatch(facts(accepted(GameAction.Tsumo), win("example:custom"))))
        val detail = batch.events.filterIsInstance<RoundSettledEvent>().single().winGroups.single().winners.single()
        assertEquals(WinMethod.SELF_DRAW, detail.method)
        assertEquals(winner, detail.playerId)
        assertEquals(game.tableState.players.map { it.id }, batch.players.map { it.playerId })
        assertEquals(batch.events.size, batch.events.map { it.eventId }.toSet().size)
    }

    /** 責任玩家本身不能把未知自訂和牌變成榮和。 */
    @Test
    fun `custom responsibility does not imply discard win`() {
        val settled = projector().project(facts(win("example:custom", responsible = listOf(other)))).filterIsInstance<RoundSettledEvent>().single()
        assertEquals(WinMethod.UNKNOWN, settled.winGroups.single().winners.single().method)
        assertEquals(emptySet(), settled.winGroups.single().discarderPlayerIds)
        assertEquals(setOf(other), settled.winGroups.single().responsiblePlayerIds)
    }

    /** 相同交易包含自訂結果時，仍保留現有內建結果 fallback。 */
    @Test
    fun `builtin fallback includes custom outcome winners without opposite result`() {
        val settled = projector().project(facts(win(BuiltInRoundOutcomeIds.TSUMO), win("example:custom", other)))
            .filterIsInstance<RoundSettledEvent>().single()
        assertEquals(listOf(WinMethod.SELF_DRAW, WinMethod.SELF_DRAW), settled.winGroups.flatMap { it.winners }.map { it.method })
    }

    /** 多組不同放銃來源保留群組，不因合併受益者而失去關係。 */
    @Test
    fun `discard groups retain separate responsible sets`() {
        val settled = projector().project(
            facts(
                HistoryEventDraft(null, HistoryFact.ReactionResolved(GameAction.Ron(Uuid.random()), null)),
                win("example:first", winner, listOf(other)),
                win("example:second", other, listOf(winner)),
            ),
        ).filterIsInstance<RoundSettledEvent>().single()
        assertEquals(listOf(setOf(other), setOf(winner)), settled.winGroups.map { it.discarderPlayerIds })
    }

    /** 相同條目 ID 在不同欄位保持分組，牌面展示欄位不混入事件的數值與條目詳情。 */
    @Test
    fun `detail groups preserve namespaces without tile identities`() {
        val fields = listOf(
            WinSettlementDetailField("example:first", WinSettlementDetailValue.Entries(listOf(WinSettlementDetailEntry("example:mark")))),
            WinSettlementDetailField("other:first", WinSettlementDetailValue.Entries(listOf(WinSettlementDetailEntry("example:mark")))),
            WinSettlementDetailField("example:tiles", WinSettlementDetailValue.TileFaces(listOf(TileFaceView(Tile.Honor.North)))),
        )
        val result = projector().project(facts(HistoryEventDraft(null, HistoryFact.WinSettled("example:win", listOf(HistoryWinDetails(winner, fields))))))
            .filterIsInstance<RoundSettledEvent>().single().winGroups.single().winners.single()
        assertEquals(listOf("example:first", "other:first"), result.detailFields.map { it.id })
    }

    /** 新局桌況不能取代上一局的完成局數。 */
    @Test
    fun `completed round uses previous round rather than new round`() {
        val summary = summary()
        val next = game.copy(tableState = FakeTableStateFactory.create(id = game.id, players = game.tableState.players, config = RiichiRuleConfig(), roundNumber = 2))
        val submitted = CommittedGameFacts(game.id, game, next, listOf(HistoryEventDraft(null, HistoryFact.RoundCompleted(summary))))
        val events = projector().project(submitted)
        assertEquals(1, events.filterIsInstance<RoundCompletedEvent>().single().roundContext.roundNumber)
        assertFalse(events.any { it is RoundSettledEvent })
    }

    /** 沒有場風的規則不公開內部預設值，有場風時只公開規則明確提供的 ID。 */
    @Test
    fun `round context only exposes a wind explicitly provided by the rule`() {
        val completed = facts(HistoryEventDraft(null, HistoryFact.RoundCompleted(summary())))
        assertNull(projector().project(completed).filterIsInstance<RoundCompletedEvent>().single().roundContext.prevalentWindId)
        val resolver = object : GameEventPublicMarkerResolver {
            override fun resolvePrevalentWindId(state: TableState) = "example:wind/north"
        }
        assertEquals(
            "example:wind/north",
            projector(registry(resolver)).project(completed).filterIsInstance<RoundCompletedEvent>().single().roundContext.prevalentWindId,
        )
    }

    /** 規則標記使用前後資料，一位贏家只詢問一次，不靠先前開始事件。 */
    @Test
    fun `rule markers use supplied winner and previous state once`() {
        var calls = 0
        val resolver = object : GameEventPublicMarkerResolver {
            override fun resolveWinMarkers(context: WinPublicMarkerContext): List<RulePublicEventMarker> {
                calls++
                assertEquals(winner, context.winnerId)
                assertSame(game.tableState, context.previousState)
                return listOf(RulePublicEventMarker("example:public", winner))
            }
        }
        val registry = registry(resolver)
        val result = projector(registry).project(facts(win("example:win"))).filterIsInstance<RoundSettledEvent>().single()
        assertEquals("example:public", result.winGroups.single().winners.single().publicMarkers.single().id)
        assertEquals(1, calls)
    }

    /** 無效或失敗的標記不吞掉核心結算；取消不能偽裝成空標記。 */
    @Test
    fun `invalid markers are diagnosed and cancellation propagates`() {
        val failures = mutableListOf<Throwable>()
        val reporter = object : GameEventProjectionReporter {
            override fun markerFailed(ruleModuleId: String, matchId: Uuid, eventKind: String, winnerId: Uuid?, cause: Throwable) {
                failures += cause
            }
        }
        val invalid = object : GameEventPublicMarkerResolver {
            override fun resolveWinMarkers(context: WinPublicMarkerContext) = listOf(RulePublicEventMarker("example:public", Uuid.random()))
        }
        val settled = GameEventProjector(modules, markerResolvers = registry(invalid), projectionReporter = reporter)
            .project(facts(win("example:win"))).filterIsInstance<RoundSettledEvent>().single()
        assertEquals(emptyList(), settled.winGroups.single().winners.single().publicMarkers)
        assertEquals(1, failures.size)
        val cancelling = object : GameEventPublicMarkerResolver {
            override fun resolveWinMarkers(context: WinPublicMarkerContext): List<RulePublicEventMarker> = throw CancellationException("cancel")
        }
        assertFailsWith<CancellationException> { projector(registry(cancelling)).project(facts(win("example:win"))) }
    }

    /** 沒有公開語意的普通摸牌不建立空批次。 */
    @Test
    fun `ordinary draw produces no event batch`() {
        assertNull(projector().projectBatch(facts(accepted(GameAction.Draw))))
    }

    /** 終局只列出實際完成名單，中止不偽造完成玩家。 */
    @Test
    fun `match completion carries exact completed ids and end markers`() {
        val resolver = object : GameEventPublicMarkerResolver {
            override fun resolveMatchEndMarkers(context: MatchEndPublicMarkerContext) = listOf(
                RulePublicEventMarker("example:threshold", quantity = RulePublicEventQuantity("example:points", -100)),
            )
        }
        val event = projector(registry(resolver)).project(facts(HistoryEventDraft(null, HistoryFact.MatchCompleted("example:end", mapOf(winner to 1)))))
            .filterIsInstance<MatchEndedEvent>().single()
        assertEquals(setOf(winner), event.completedPlayerIds)
        assertEquals(-100, event.publicMarkers.single().quantity?.amount)
        val aborted = projector().project(CommittedGameFacts(game.id, game, null, listOf(HistoryEventDraft(null, HistoryFact.MatchAborted("example:aborted")))))
            .filterIsInstance<MatchEndedEvent>().single()
        assertEquals(emptySet(), aborted.completedPlayerIds)
    }

    /** 建立測試投影器。 */
    private fun projector(registry: GameEventPublicMarkerResolverRegistry = GameEventPublicMarkerResolverRegistry()) = GameEventProjector(modules, markerResolvers = registry)

    /** 登記測試專用的規則標記來源。 */
    private fun registry(resolver: GameEventPublicMarkerResolver) = GameEventPublicMarkerResolverRegistry().apply {
        register(GameEventPublicMarkerResolverDefinition("mahjongcraft:riichi", resolver))
    }

    /** 建立同一來源對局的已提交事實。 */
    private fun facts(vararg drafts: HistoryEventDraft) = CommittedGameFacts(game.id, game, game, drafts.toList())

    /** 建立單一贏家的測試和牌。 */
    private fun win(id: String, playerId: Uuid = winner, responsible: List<Uuid> = emptyList()) = HistoryEventDraft(null, HistoryFact.WinSettled(id, listOf(HistoryWinDetails(playerId, emptyList())), responsible))

    /** 建立不帶隱藏資料的測試動作。 */
    private fun accepted(action: GameAction) = HistoryEventDraft(
        winner,
        HistoryFact.ActionAccepted(action, HistoryActionResult(emptyList(), emptyList(), 1, emptyList(), emptyMap(), winner)),
    )

    /** 建立本局完成摘要。 */
    private fun summary() = RoundCompletionSummary(
        outcomeId = "example:exhaustive",
        classification = RoundCompletionClassification.EXHAUSTIVE_DRAW,
        beneficiaryPlayerIds = emptySet(),
        transitionDirective = RoundTransitionDirective.PassToNextSeat,
        settledScoresByPlayerId = game.tableState.players.associate { it.id to it.score },
    )
}
