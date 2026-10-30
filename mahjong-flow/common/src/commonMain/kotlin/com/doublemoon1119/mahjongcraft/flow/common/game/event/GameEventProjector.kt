package com.doublemoon1119.mahjongcraft.flow.common.game.event

import com.doublemoon1119.mahjongcraft.flow.api.event.ActionsPerformedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch
import com.doublemoon1119.mahjongcraft.flow.api.event.EventQuantity
import com.doublemoon1119.mahjongcraft.flow.api.event.EventRoundContext
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchCompletion
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEndedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchPlayer
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchStanding
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchStartedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.PublicEventMarker
import com.doublemoon1119.mahjongcraft.flow.api.event.ReadOnlyMapSnapshot
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundCompletedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundCompletionKind
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundScoreChange
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettledEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettlementKind
import com.doublemoon1119.mahjongcraft.flow.api.event.RuleResultCompletion
import com.doublemoon1119.mahjongcraft.flow.api.event.RuleResultDetail
import com.doublemoon1119.mahjongcraft.flow.api.event.RuleResultsResolvedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.WinMethod
import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RecordedDrawOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.newlyRecordedActionsByPlayerId
import com.doublemoon1119.mahjongcraft.flow.common.game.model.recordedDrawOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.roundRanksByPlayer
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 由一次權威交易提交的事實產生對局事件。
 *
 * 每一種事實都明確決定是否產生事件：
 * - 對局開始：[HistoryFact.MatchStarted]。
 * - 結算：在實際計分的交易產生，同一筆交易最多一次。有 [HistoryFact.WinSettled] 時為和牌（一炮多響也只有一次）；
 *   否則有帶本局結算摘要的 [HistoryFact.RuleEffectResolved] 時為特殊結果；否則這筆交易寫入流局的本局結算摘要、或新增了
 *   流局記錄時為流局。
 *   結算前後的分數取交易前後的對局。換局交易的 [HistoryFact.RoundCompleted] 另產生本局完成事件，不重發結算。
 * - 對局結束：[HistoryFact.MatchCompleted] 或移除交易的 [HistoryFact.MatchAborted]，名次依規則的終局排名決定。
 *
 * @property moduleRegistry 取得對局的規則模組，用來查詢規則 ID 與排名比較器。
 * @property newEventId 產生事件 ID。
 * @property markerResolvers 規則提供的公開事件標記，不直接辨識規則狀態。
 * @property projectionReporter 回報標記投影失敗。
 * @property failurePolicy 決定擴充失敗能否隔離。
 */
class GameEventProjector(
    private val moduleRegistry: MahjongModuleRegistry,
    private val newEventId: () -> Uuid = Uuid::random,
    private val markerResolvers: GameEventPublicMarkerResolverRegistry = GameEventPublicMarkerResolverRegistry(),
    private val projectionReporter: GameEventProjectionReporter = GameEventProjectionReporter.NONE,
    private val failurePolicy: ExtensionFailurePolicy = ExtensionFailurePolicy.Default,
) {
    /**
     * 同一次投影取得公開批次及標記失敗狀態，彼此不共享可變狀態。
     *
     * @param facts 已提交交易。
     * @return 公開批次與本次標記解析狀態。
     */
    fun projectBatchResult(facts: CommittedGameFacts): GameEventProjectionResult {
        var markersFailed = false
        val trackingReporter = object : GameEventProjectionReporter {
            override fun markerFailed(ruleModuleId: String, matchId: Uuid, eventKind: String, winnerId: Uuid?, cause: Throwable) {
                markersFailed = true
                projectionReporter.markerFailed(ruleModuleId, matchId, eventKind, winnerId, cause)
            }
        }
        val batch = GameEventProjector(
            moduleRegistry = moduleRegistry,
            newEventId = newEventId,
            markerResolvers = markerResolvers,
            projectionReporter = trackingReporter,
            failurePolicy = failurePolicy,
        ).projectBatch(facts)
        return GameEventProjectionResult(batch, markersFailed)
    }

    /**
     * 投影一次交易，保留玩家名單與同筆通知的邊界；沒有公開通知時不建立空批次。
     *
     * @param facts 已提交交易。
     * @return 只讀事件批次；沒有公開通知時為 null。
     */
    fun projectBatch(facts: CommittedGameFacts): CommittedMatchEventBatch? {
        val events = project(facts)
        if (events.isEmpty()) return null
        val game = facts.game ?: checkNotNull(facts.previousGame)
        return CommittedMatchEventBatch.create(
            batchId = newEventId(),
            matchId = game.matchId,
            venueId = game.id,
            ruleModuleId = moduleRegistry.getModule(game.tableState.config).id,
            players = game.tableState.players.map { MatchPlayer.create(it.id, game.isAi(it.id), it.initialSeatIndex) },
            events = events,
        )
    }

    /**
     * 產生 [facts] 對應的事件，依開始、成立動作、結算、本局完成、終局排列；舊三種事件的相對順序不變。
     *
     * @param facts 一次權威交易對單一場地提交的事實。
     * @return 對應的事件；沒有任何事件時為空清單。
     */
    fun project(facts: CommittedGameFacts): List<MatchEvent> {
        val game = facts.game ?: run {
            val previous = facts.previousGame ?: return emptyList()
            val aborted = facts.facts.mapNotNull { it.fact as? HistoryFact.MatchAborted }.firstOrNull() ?: return emptyList()
            if (previous.isMatchOver) return emptyList()
            val module = moduleRegistry.getModule(previous.tableState.config)
            return listOf(matchEnded(previous, module, aborted.reasonId, MatchCompletion.ABORTED, facts))
        }
        val module = moduleRegistry.getModule(game.tableState.config)
        var started: MatchStartedEvent? = null
        val wins = mutableListOf<HistoryFact.WinSettled>()
        var specialOutcome: RoundCompletionSummary? = null
        var ended: MatchEndedEvent? = null
        val completed = mutableListOf<RoundCompletedEvent>()
        facts.facts.forEach { draft ->
            when (val fact = draft.fact) {
                is HistoryFact.MatchStarted -> started = matchStarted(game, module)
                is HistoryFact.WinSettled -> wins += fact
                is HistoryFact.RuleEffectResolved -> fact.roundCompletion?.let { specialOutcome = specialOutcome ?: it }
                is HistoryFact.MatchCompleted -> ended = matchEnded(
                    game,
                    module,
                    fact.reasonId,
                    MatchCompletion.COMPLETED,
                    facts,
                    fact.finalScoresByPlayerId.keys,
                    fact.finalScoresByPlayerId,
                )
                is HistoryFact.RoundCompleted -> completed += roundCompleted(facts, module, fact.summary)
                is HistoryFact.MatchAborted -> Unit
                is HistoryFact.RoundStarted,
                is HistoryFact.DiceRolled,
                is HistoryFact.RuleDecisionStarted,
                is HistoryFact.RuleDecisionSubmitted,
                is HistoryFact.RuleDecisionAutomaticallyResolved,
                is HistoryFact.ActionAccepted,
                is HistoryFact.ActionScored,
                is HistoryFact.ReactionResolved,
                is HistoryFact.WinContinuationResolved,
                is HistoryFact.TableChanged,
                is HistoryFact.TilePresentationChanged,
                HistoryFact.ReturnedToRoom,
                -> Unit
            }
        }
        val settled = facts.previousGame?.let { previous -> roundSettled(previous, game, module, wins, specialOutcome, facts) }
        val actions = GameActionEventProjector.project(facts, module).takeIf { it.isNotEmpty() }?.let {
            ActionsPerformedEvent.create(newEventId(), game.matchId, game.id, module.id, game.tableState.roundNumber, it)
        }
        val ruleResults = facts.facts.mapNotNull { draft ->
            (draft.fact as? HistoryFact.RuleEffectResolved)?.let { result ->
                RuleResultDetail.create(
                    reasonId = result.reasonId,
                    winDetails = result.winDetails.map { details -> GameWinEventProjector.detail(details, WinMethod.UNKNOWN, emptyList()) },
                    completion = result.roundCompletion?.let { summary ->
                        RuleResultCompletion.create(
                            outcomeId = summary.outcomeId,
                            kind = completionKind(summary.classification),
                            beneficiaryPlayerIds = summary.beneficiaryPlayerIds,
                        )
                    },
                )
            }
        }.takeIf { it.isNotEmpty() }?.let { results ->
            RuleResultsResolvedEvent.create(
                eventId = newEventId(),
                matchId = game.matchId,
                venueId = game.id,
                ruleModuleId = module.id,
                results = results,
            )
        }
        return listOfNotNull(started, actions, settled, ruleResults) + completed + listOfNotNull(ended)
    }

    /**
     * 建立包含開局玩家座位的對局開始事件。
     *
     * @param game 開局交易提交後的對局。
     * @param module 對局使用的規則模組。
     * @return 新的對局開始事件。
     */
    private fun matchStarted(game: Game, module: MahjongRuleModule<*>): MatchStartedEvent = MatchStartedEvent.create(
        eventId = newEventId(),
        matchId = game.matchId,
        venueId = game.id,
        ruleModuleId = module.id,
        players = game.tableState.players.map { player ->
            MatchPlayer.create(playerId = player.id, isAi = game.isAi(player.id), initialSeatIndex = player.initialSeatIndex)
        },
    )

    /**
     * 建立這筆交易的結算事件；沒有結算時為 null。
     *
     * @param previous 交易前的對局。
     * @param game 交易後的對局。
     * @param module 提供回合排名規則的模組。
     * @param wins 本次交易提交的和牌結算事實。
     * @param specialOutcome 本次交易直接產生的特殊結算摘要；沒有時為 null。
     * @param facts 取得同筆動作分類與規則標記的來源。
     * @return 合併同筆交易贏家與分數變化的結算事件；無結算時為 null。
     */
    private fun roundSettled(
        previous: Game,
        game: Game,
        module: MahjongRuleModule<*>,
        wins: List<HistoryFact.WinSettled>,
        specialOutcome: RoundCompletionSummary?,
        facts: CommittedGameFacts,
    ): RoundSettledEvent? {
        val settlement = when {
            wins.isNotEmpty() -> Settlement(
                outcomeId = wins.first().outcomeId,
                kind = RoundSettlementKind.WIN,
                beneficiaryPlayerIds = wins.flatMapTo(linkedSetOf()) { win -> win.winDetails.map { it.playerId } },
                responsiblePlayerIds = wins.flatMapTo(linkedSetOf()) { it.responsiblePlayerIds },
            )
            specialOutcome != null -> Settlement(specialOutcome, RoundSettlementKind.SPECIAL)
            else -> drawCompletion(previous, game)
        } ?: return null
        val previousRanks = roundRanksByPlayer(previous.tableState, module)
        val currentRanks = roundRanksByPlayer(game.tableState, module)
        val previousScores = previous.tableState.players.associate { it.id to it.score }
        return RoundSettledEvent.create(
            eventId = newEventId(),
            matchId = game.matchId,
            venueId = game.id,
            ruleModuleId = module.id,
            roundNumber = game.tableState.roundNumber,
            outcomeId = settlement.outcomeId,
            kind = settlement.kind,
            beneficiaryPlayerIds = settlement.beneficiaryPlayerIds,
            responsiblePlayerIds = settlement.responsiblePlayerIds,
            players = game.tableState.players.map { player ->
                RoundScoreChange.create(
                    playerId = player.id,
                    previousScore = previousScores.getValue(player.id),
                    currentScore = player.score,
                    previousRank = previousRanks.getValue(player.id),
                    currentRank = currentRanks.getValue(player.id),
                )
            },
            winGroups = GameWinEventProjector.project(facts) { outcomeId, winnerId, fields ->
                publicMarkers(facts, module.id, "win", winnerId) { resolver ->
                    resolver.resolveWinMarkers(WinPublicMarkerContext(module.id, facts.matchId, outcomeId, winnerId, previous.tableState, game.tableState, fields))
                }
            },
        )
    }

    /**
     * 建立這筆交易的流局事件資料；沒有流局時為 null。
     *
     * 規則的流局交易同時寫入摘要時使用它（荒牌流局沒有人聽牌時，玩家的動作記錄不會新增流局，只能由摘要得知）；否則依這筆
     * 交易新增的流局記錄建立，例如由反應或動作後判定直接產生、摘要之後才補上的途中流局。
     *
     * @param previous 交易前的對局。
     * @param game 交易後的對局。
     * @return 本次交易產生的流局事件資料；沒有時為 null。
     */
    private fun drawCompletion(previous: Game, game: Game): Settlement? {
        val committed = game.roundCompletion
            ?.takeIf { previous.roundCompletion == null && it.classification in DRAW_CLASSIFICATIONS }
            ?.let { Settlement(it, RoundSettlementKind.DRAW) }
        return committed ?: recordedDrawOutcome(
            game.tableState,
            newlyRecordedActionsByPlayerId(previous.tableState, game.tableState),
        )?.let { Settlement(it, RoundSettlementKind.DRAW) }
    }

    /**
     * 由指定對局快照建立終局名次，不重新結算分數。
     *
     * @param game 終局或移除前的對局快照。
     * @param module 提供終局排名比較器的規則模組。
     * @param reasonId 原樣轉交的終局原因 ID。
     * @param completion 正常完成或中途終止的分類。
     * @param facts 產生終局標記的前後來源。
     * @param completedPlayerIds 正常完成名單；中止時為空。
     * @param completedScores 正常終局提交的明確分數；中止時為空。
     * @return 依規則排名的對局結束事件。
     */
    private fun matchEnded(
        game: Game,
        module: MahjongRuleModule<*>,
        reasonId: String,
        completion: MatchCompletion,
        facts: CommittedGameFacts,
        completedPlayerIds: Set<Uuid> = emptySet(),
        completedScores: Map<Uuid, Int> = emptyMap(),
    ): MatchEndedEvent = MatchEndedEvent.create(
        eventId = newEventId(),
        matchId = game.matchId,
        venueId = game.id,
        ruleModuleId = module.id,
        completion = completion,
        reasonId = reasonId,
        standings = game.tableState.players.sortedWith(module.compareForMatchRanking()).mapIndexed { index, player ->
            MatchStanding.create(playerId = player.id, isAi = game.isAi(player.id), score = player.score, rank = index + 1)
        },
        roundContext = roundContext(facts, module.id, (facts.previousGame ?: game).tableState),
        publicMarkers = publicMarkers(facts, module.id, "match_end", null) { resolver ->
            resolver.resolveMatchEndMarkers(
                MatchEndPublicMarkerContext(
                    module.id,
                    facts.matchId,
                    completion,
                    reasonId,
                    (facts.previousGame ?: game).tableState,
                    facts.game?.tableState,
                    ReadOnlyMapSnapshot(
                        if (completion == MatchCompletion.COMPLETED) completedScores else game.tableState.players.associate { it.id to it.score },
                    ),
                ),
            )
        },
        completedPlayerIds = completedPlayerIds,
        completedScores = completedScores,
    )

    /** 建立完成前局況與結算分數，不以換局後新桌況代替。 */
    private fun roundCompleted(facts: CommittedGameFacts, module: MahjongRuleModule<*>, summary: RoundCompletionSummary): RoundCompletedEvent {
        val game = facts.previousGame ?: checkNotNull(facts.game)
        val ranks = roundRanksByPlayer(game.tableState, module)
        return RoundCompletedEvent.create(
            eventId = newEventId(),
            matchId = game.matchId,
            venueId = game.id,
            ruleModuleId = module.id,
            roundContext = roundContext(facts, module.id, game.tableState),
            classification = when (summary.classification) {
                RoundCompletionClassification.WIN -> RoundCompletionKind.WIN
                RoundCompletionClassification.EXHAUSTIVE_DRAW -> RoundCompletionKind.EXHAUSTIVE_DRAW
                RoundCompletionClassification.ABORTIVE_DRAW -> RoundCompletionKind.ABORTIVE_DRAW
                RoundCompletionClassification.EXTENSION -> RoundCompletionKind.SPECIAL
            },
            outcomeId = summary.outcomeId,
            players = game.tableState.players.filter { it.id in summary.settledScoresByPlayerId }.map {
                val score = summary.settledScoresByPlayerId.getValue(it.id)
                RoundScoreChange.create(it.id, score, score, ranks.getValue(it.id), ranks.getValue(it.id))
            },
        )
    }

    /** 將已提交的結算分類轉成公開事件分類。 */
    private fun completionKind(classification: RoundCompletionClassification): RoundCompletionKind = when (classification) {
        RoundCompletionClassification.WIN -> RoundCompletionKind.WIN
        RoundCompletionClassification.EXHAUSTIVE_DRAW -> RoundCompletionKind.EXHAUSTIVE_DRAW
        RoundCompletionClassification.ABORTIVE_DRAW -> RoundCompletionKind.ABORTIVE_DRAW
        RoundCompletionClassification.EXTENSION -> RoundCompletionKind.SPECIAL
    }

    /** 保留完成前局數，場風只使用規則明確公開的值，不能把預設東風當作規則能力。 */
    private fun roundContext(facts: CommittedGameFacts, ruleId: String, state: TableState): EventRoundContext {
        val windId = try {
            markerResolvers.find(ruleId)?.resolvePrevalentWindId(state)?.also {
                NamespacedId.requireValid(it) { "Public prevalent wind ID must be namespaced: $it" }
            }
        } catch (cause: Throwable) {
            if (!failurePolicy.isIsolatable(cause)) throw cause
            reportMarkerFailure(ruleId, facts.matchId, "round_context", null, cause)
            null
        }
        return EventRoundContext.create(state.roundNumber, windId)
    }

    /**
     * 檢查規則提供的標記，失敗時只略過該次附加資料。
     *
     * @param facts 來源交易。
     * @param ruleId 規則 ID。
     * @param kind 公開事件種類。
     * @param winnerId 和牌贏家；終局時為 null。
     * @param resolve 規則的純讀取方法。
     * @return 已複製的公開標記。
     */
    private fun publicMarkers(
        facts: CommittedGameFacts,
        ruleId: String,
        kind: String,
        winnerId: Uuid?,
        resolve: (GameEventPublicMarkerResolver) -> List<RulePublicEventMarker>,
    ): List<PublicEventMarker> {
        val resolver = markerResolvers.find(ruleId) ?: return emptyList()
        return try {
            val markers = resolve(resolver)
            val players = (facts.game ?: checkNotNull(facts.previousGame)).tableState.players.map { it.id }.toSet()
            require(markers.map { it.id to it.playerId }.distinct().size == markers.size) { "Duplicate public event markers" }
            require(markers.all { it.playerId == null || it.playerId in players }) { "Marker player does not belong to match" }
            require(winnerId == null || markers.all { it.playerId == winnerId }) { "Win marker belongs to another player" }
            markers.map { marker ->
                PublicEventMarker.create(marker.id, marker.playerId, marker.quantity?.let { EventQuantity.create(it.unitId, it.amount) })
            }
        } catch (cause: Throwable) {
            if (!failurePolicy.isIsolatable(cause)) throw cause
            reportMarkerFailure(ruleId, facts.matchId, kind, winnerId, cause)
            emptyList()
        }
    }

    /** 回報附加投影失敗，回報器的可隔離錯誤不遮蓋原事件。 */
    private fun reportMarkerFailure(ruleId: String, matchId: Uuid, kind: String, winnerId: Uuid?, cause: Throwable) {
        try {
            projectionReporter.markerFailed(ruleId, matchId, kind, winnerId, cause)
        } catch (reportFailure: Throwable) {
            if (!failurePolicy.isIsolatable(reportFailure)) throw reportFailure
        }
    }

    /** [DRAW_CLASSIFICATIONS] 所在的伴生物件。 */
    private companion object {
        /** 屬於流局的本局結算分類。 */
        val DRAW_CLASSIFICATIONS = setOf(RoundCompletionClassification.EXHAUSTIVE_DRAW, RoundCompletionClassification.ABORTIVE_DRAW)
    }

    /**
     * 結算事件的結果內容。
     *
     * @property outcomeId 結算結果 ID。
     * @property kind 結算的種類。
     * @property beneficiaryPlayerIds 得利的玩家。
     * @property responsiblePlayerIds 負責的玩家。
     */
    private class Settlement(
        val outcomeId: String,
        val kind: RoundSettlementKind,
        val beneficiaryPlayerIds: Set<Uuid>,
        val responsiblePlayerIds: Set<Uuid>,
    ) {
        /**
         * 由本局結算摘要建立事件內容。
         *
         * @param summary 本局結算的權威摘要。
         * @param kind 此事件採用的結算分類。
         */
        constructor(summary: RoundCompletionSummary, kind: RoundSettlementKind) : this(
            outcomeId = summary.outcomeId,
            kind = kind,
            beneficiaryPlayerIds = summary.beneficiaryPlayerIds,
            responsiblePlayerIds = summary.responsiblePlayerIds,
        )

        /**
         * 由交易中新增的流局事實建立事件內容；不使用或重新計算莊家推進決策。
         *
         * @param outcome 流局的純結果資料。
         * @param kind 此事件採用的結算分類。
         */
        constructor(outcome: RecordedDrawOutcome, kind: RoundSettlementKind) : this(
            outcomeId = outcome.outcomeId,
            kind = kind,
            beneficiaryPlayerIds = outcome.beneficiaryPlayerIds,
            responsiblePlayerIds = outcome.responsiblePlayerIds,
        )
    }
}
