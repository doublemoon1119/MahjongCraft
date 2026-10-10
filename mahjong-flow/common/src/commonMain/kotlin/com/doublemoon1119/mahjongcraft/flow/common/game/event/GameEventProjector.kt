package com.doublemoon1119.mahjongcraft.flow.common.game.event

import com.doublemoon1119.mahjongcraft.flow.api.event.MatchCompletion
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEndedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchPlayer
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchStanding
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchStartedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundScoreChange
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettledEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettlementKind
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.newlyRecordedActionsByPlayerId
import com.doublemoon1119.mahjongcraft.flow.common.game.model.recordedDrawCompletion
import com.doublemoon1119.mahjongcraft.flow.common.game.model.roundRanksByPlayer
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import kotlin.uuid.Uuid

/**
 * 由一次權威交易提交的事實產生對局事件。
 *
 * 每一種事實都明確決定是否產生事件：
 * - 對局開始：[HistoryFact.MatchStarted]。
 * - 結算：在實際計分的交易產生，同一筆交易最多一次。有 [HistoryFact.WinSettled] 時為和牌（一炮多響也只有一次）；
 *   否則有帶本局結算摘要的 [HistoryFact.RuleEffectResolved] 時為特殊結果；否則這筆交易寫入流局的本局結算摘要、或新增了
 *   流局記錄時為流局。
 *   結算前後的分數取交易前後的對局。換局交易的 [HistoryFact.RoundCompleted] 不產生事件。
 * - 對局結束：[HistoryFact.MatchCompleted] 或移除交易的 [HistoryFact.MatchAborted]，名次依規則的終局排名決定。
 *
 * @property moduleRegistry 取得對局的規則模組，用來查詢規則 ID 與排名比較器。
 * @property newEventId 產生事件 ID。
 */
class GameEventProjector(
    private val moduleRegistry: MahjongModuleRegistry,
    private val newEventId: () -> Uuid = Uuid::random,
) {
    /**
     * 產生 [facts] 對應的事件，依對局開始、結算、對局結束的順序排列。
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
            return listOf(matchEnded(previous, module, aborted.reasonId, MatchCompletion.ABORTED))
        }
        val module = moduleRegistry.getModule(game.tableState.config)
        var started: MatchStartedEvent? = null
        val wins = mutableListOf<HistoryFact.WinSettled>()
        var specialOutcome: RoundCompletionSummary? = null
        var ended: MatchEndedEvent? = null
        facts.facts.forEach { draft ->
            when (val fact = draft.fact) {
                is HistoryFact.MatchStarted -> started = matchStarted(game, module)
                is HistoryFact.WinSettled -> wins += fact
                is HistoryFact.RuleEffectResolved -> fact.roundCompletion?.let { specialOutcome = specialOutcome ?: it }
                is HistoryFact.MatchCompleted -> ended = matchCompleted(game, module, fact.reasonId)
                is HistoryFact.MatchAborted -> Unit
                is HistoryFact.RoundStarted,
                is HistoryFact.RoundPreparationStarted,
                is HistoryFact.RoundPreparationSubmitted,
                is HistoryFact.RoundPreparationAutomaticallyResolved,
                is HistoryFact.ActionAccepted,
                is HistoryFact.ReactionResolved,
                is HistoryFact.RoundCompleted,
                is HistoryFact.WinContinuationResolved,
                is HistoryFact.TableChanged,
                HistoryFact.ReturnedToRoom,
                -> Unit
            }
        }
        val settled = facts.previousGame?.let { previous -> roundSettled(previous, game, module, wins, specialOutcome) }
        return listOfNotNull(started, settled, ended)
    }

    /** 對局開始的事件。 */
    private fun matchStarted(game: Game, module: MahjongRuleModule<*>): MatchStartedEvent = MatchStartedEvent.create(
        eventId = newEventId(),
        matchId = game.matchId,
        venueId = game.id,
        ruleModuleId = module.id,
        players = game.tableState.players.map { player ->
            MatchPlayer.create(playerId = player.id, isAi = game.isAi(player.id), initialSeatIndex = player.initialSeatIndex)
        },
    )

    /** 這筆交易的結算事件；這筆交易沒有結算時為 null。 */
    private fun roundSettled(
        previous: Game,
        game: Game,
        module: MahjongRuleModule<*>,
        wins: List<HistoryFact.WinSettled>,
        specialOutcome: RoundCompletionSummary?,
    ): RoundSettledEvent? {
        val settlement = when {
            wins.isNotEmpty() -> Settlement(
                outcomeId = wins.first().outcomeId,
                kind = RoundSettlementKind.WIN,
                beneficiaryPlayerIds = wins.flatMapTo(linkedSetOf()) { win -> win.winDetails.map { it.playerId } },
                responsiblePlayerIds = wins.flatMapTo(linkedSetOf()) { it.responsiblePlayerIds },
            )
            specialOutcome != null -> Settlement(specialOutcome, RoundSettlementKind.SPECIAL)
            else -> drawCompletion(previous, game)?.let { Settlement(it, RoundSettlementKind.DRAW) }
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
        )
    }

    /**
     * 這筆交易流局時的結算摘要；沒有流局時為 null。
     *
     * 規則的流局交易同時寫入摘要時使用它（荒牌流局沒有人聽牌時，玩家的動作記錄不會新增流局，只能由摘要得知）；否則依這筆
     * 交易新增的流局記錄建立，例如由反應或動作後判定直接產生、摘要之後才補上的途中流局。
     */
    private fun drawCompletion(previous: Game, game: Game): RoundCompletionSummary? {
        val committed = game.roundCompletion?.takeIf { previous.roundCompletion == null && it.classification in DRAW_CLASSIFICATIONS }
        return committed ?: recordedDrawCompletion(game.tableState, newlyRecordedActionsByPlayerId(previous.tableState, game.tableState))
    }

    /** 正常打完的對局結束事件。 */
    private fun matchCompleted(game: Game, module: MahjongRuleModule<*>, reasonId: String): MatchEndedEvent = matchEnded(game, module, reasonId, MatchCompletion.COMPLETED)

    /**
     * 由指定對局快照建立終局名次，不重新結算分數。
     *
     * @param game 終局或移除前的對局快照。
     * @param module 提供終局排名比較器的規則模組。
     * @param reasonId 原樣轉交的終局原因 ID。
     * @param completion 正常完成或中途終止的分類。
     * @return 依規則排名的對局結束事件。
     */
    private fun matchEnded(
        game: Game,
        module: MahjongRuleModule<*>,
        reasonId: String,
        completion: MatchCompletion,
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
    )

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
        /** 由本局結算摘要建立。 */
        constructor(summary: RoundCompletionSummary, kind: RoundSettlementKind) : this(
            outcomeId = summary.outcomeId,
            kind = kind,
            beneficiaryPlayerIds = summary.beneficiaryPlayerIds,
            responsiblePlayerIds = summary.responsiblePlayerIds,
        )
    }
}
