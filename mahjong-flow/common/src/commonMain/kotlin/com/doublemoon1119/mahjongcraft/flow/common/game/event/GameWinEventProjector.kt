package com.doublemoon1119.mahjongcraft.flow.common.game.event

import com.doublemoon1119.mahjongcraft.flow.api.event.EventQuantity
import com.doublemoon1119.mahjongcraft.flow.api.event.PublicEventMarker
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundWinDetail
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundWinGroup
import com.doublemoon1119.mahjongcraft.flow.api.event.WinDetailField
import com.doublemoon1119.mahjongcraft.flow.api.event.WinGroupSource
import com.doublemoon1119.mahjongcraft.flow.api.event.WinMethod
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import kotlin.uuid.Uuid

/** 將既有結算來源分類成公開和牌群組，不從責任名單猜測和牌方式。 */
internal object GameWinEventProjector {
    /**
     * 保留同筆交易的結算分組及已解析的公開詳情。
     *
     * @param facts 已提交交易。
     * @param markerProvider 依來源結果及贏家取得規則公開標記。
     * @return 保留來源順序的和牌群組。
     */
    fun project(
        facts: CommittedGameFacts,
        markerProvider: (String, Uuid, List<WinSettlementDetailField>) -> List<PublicEventMarker>,
    ): List<RoundWinGroup> {
        val wins = facts.facts.mapNotNull { it.fact as? HistoryFact.WinSettled }
        val tsumoPlayers = facts.facts.mapNotNull { draft ->
            (draft.fact as? HistoryFact.ActionAccepted)?.takeIf { it.action == GameAction.Tsumo }?.let { draft.actorPlayerId }
        }.toSet()
        val ron = facts.facts.any { (it.fact as? HistoryFact.ReactionResolved)?.resolvedAction is GameAction.Ron }
        val fallbackTsumo = tsumoPlayers.isEmpty() &&
            !ron &&
            wins.any { it.outcomeId == BuiltInRoundOutcomeIds.TSUMO } &&
            wins.none { it.outcomeId == BuiltInRoundOutcomeIds.RON }
        val fallbackRon = tsumoPlayers.isEmpty() &&
            !ron &&
            wins.any { it.outcomeId == BuiltInRoundOutcomeIds.RON } &&
            wins.none { it.outcomeId == BuiltInRoundOutcomeIds.TSUMO }
        return facts.facts.mapNotNull { draft ->
            when (val fact = draft.fact) {
                is HistoryFact.WinSettled -> {
                    val isRon = (ron && tsumoPlayers.isEmpty()) || fallbackRon
                    RoundWinGroup.create(
                        outcomeId = fact.outcomeId,
                        winners = fact.winDetails.map { details ->
                            detail(
                                details,
                                when {
                                    details.playerId in tsumoPlayers || fallbackTsumo -> WinMethod.SELF_DRAW
                                    isRon -> WinMethod.DISCARD
                                    else -> WinMethod.UNKNOWN
                                },
                                markerProvider(fact.outcomeId, details.playerId, details.detailFields),
                            )
                        },
                        responsiblePlayerIds = fact.responsiblePlayerIds.toSet(),
                        discarderPlayerIds = if (isRon) fact.responsiblePlayerIds.toSet() else emptySet(),
                    )
                }
                is HistoryFact.RuleEffectResolved -> {
                    val summary = fact.roundCompletion?.takeIf { it.classification == RoundCompletionClassification.WIN }
                        ?: return@mapNotNull null
                    val ids = (fact.winDetails.map { it.playerId } + summary.beneficiaryPlayerIds).distinct()
                    RoundWinGroup.create(
                        outcomeId = summary.outcomeId,
                        winners = ids.map { id ->
                            val details = fact.winDetails.firstOrNull { it.playerId == id } ?: HistoryWinDetails(id, emptyList())
                            detail(details, WinMethod.UNKNOWN, markerProvider(summary.outcomeId, id, details.detailFields))
                        },
                        responsiblePlayerIds = summary.responsiblePlayerIds,
                        discarderPlayerIds = emptySet(),
                        source = WinGroupSource.RULE_RESULT,
                    )
                }
                else -> null
            }
        }
    }

    /** 只複製 Entries／Quantities，不轉交完整手牌或牌張 UUID。 */
    internal fun detail(details: HistoryWinDetails, method: WinMethod, markers: List<PublicEventMarker>): RoundWinDetail = RoundWinDetail.create(
        playerId = details.playerId,
        method = method,
        detailFields = details.detailFields.mapNotNull { field ->
            when (val value = field.value) {
                is WinSettlementDetailValue.Entries -> WinDetailField.create(
                    id = field.id,
                    entries = value.entries.map { entry ->
                        PublicEventMarker.create(
                            id = entry.id,
                            playerId = details.playerId,
                            quantity = entry.quantity?.let { EventQuantity.create(it.unitId, it.amount) },
                        )
                    },
                    quantities = emptyList(),
                )
                is WinSettlementDetailValue.Quantities -> WinDetailField.create(
                    id = field.id,
                    entries = emptyList(),
                    quantities = value.quantities.map { EventQuantity.create(it.unitId, it.amount) },
                )
                is WinSettlementDetailValue.TileFaces -> null
            }
        },
        publicMarkers = markers,
    )
}
