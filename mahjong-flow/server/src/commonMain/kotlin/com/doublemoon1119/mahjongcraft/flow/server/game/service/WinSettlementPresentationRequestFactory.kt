package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementWinnerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 建立胡牌詳情與共用分數排行的權威快照。 */
object WinSettlementPresentationRequestFactory {
    /** 建立不偽造翻符或胡牌張的 win-equivalent 特殊 outcome request；規則專屬樣板與欄位交由 [detailResolverRegistry] 解析。 */
    fun createSpecialOutcome(
        previousState: TableState,
        outcome: ResolvedRoundOutcome,
        module: MahjongRuleModule<*>,
        detailResolverRegistry: WinSettlementDetailResolverRegistry,
    ): WinSettlementPresentationRequest {
        val currentState = outcome.settledTableState
        val previousRanks = roundRanksByPlayer(previousState, module)
        val currentRanks = roundRanksByPlayer(currentState, module)
        val resolvedDetails = detailResolverRegistry.resolveSpecialOutcome(module.id, currentState, outcome)
        return WinSettlementPresentationRequest(
            outcomeId = outcome.id,
            templateKey = resolvedDetails.templateKey,
            isTsumo = outcome.responsiblePlayerIds.isEmpty(),
            winners = outcome.beneficiaryPlayerIds.map { winnerId ->
                val player = currentState.players.first { it.id == winnerId }
                WinSettlementWinnerPresentation(
                    playerId = winnerId,
                    seatIndex = currentState.players.indexOf(player),
                    responsiblePlayerId = outcome.responsiblePlayerIds.singleOrNull(),
                    totalScore = outcome.scoreDeltas.getValue(winnerId),
                    standingTileIds = player.hand.standingTiles.map { it.id },
                    melds = player.hand.melds.map { it.toPresentation(currentState.config.revealsClosedKanTiles) },
                    winningTileId = null,
                    detailFields = resolvedDetails.fields,
                )
            },
            ranking = ScoreRankingPresentation(
                currentState.players.mapIndexed { seatIndex, player ->
                    val previous = previousState.players.first { it.id == player.id }
                    ScoreRankingPlayer(
                        player.id,
                        seatIndex,
                        player.isAi,
                        previous.score,
                        player.score,
                        previousRanks.getValue(player.id),
                        currentRanks.getValue(player.id),
                    )
                },
            ),
        )
    }

    /** 建立一般自摸／榮和 request；規則專屬欄位在此轉成穩定、可序列化的 detail values。 */
    fun create(
        previousState: TableState,
        currentState: TableState,
        module: MahjongRuleModule<*>,
        outcomeId: String,
        isTsumo: Boolean,
        winningTileId: Uuid,
        responsiblePlayerId: Uuid?,
        resolutions: Map<Uuid, WinResolutionResult>,
        detailResolverRegistry: WinSettlementDetailResolverRegistry,
    ): WinSettlementPresentationRequest {
        val previousRanks = roundRanksByPlayer(previousState, module)
        val currentRanks = roundRanksByPlayer(currentState, module)
        val resolvedDetails = resolutions.mapValues { (_, resolution) ->
            detailResolverRegistry.resolve(module.id, currentState, resolution.handValueResult)
        }
        return WinSettlementPresentationRequest(
            outcomeId = outcomeId,
            templateKey = resolvedDetails.values.map(WinSettlementResolvedDetails::templateKey).distinct().singleOrNull()
                ?: GENERIC_TEMPLATE_KEY,
            isTsumo = isTsumo,
            winners = resolutions.map { (winnerId, resolution) ->
                val player = currentState.players.first { it.id == winnerId }
                WinSettlementWinnerPresentation(
                    playerId = winnerId,
                    seatIndex = currentState.players.indexOf(player),
                    responsiblePlayerId = responsiblePlayerId,
                    totalScore = resolution.totalGained,
                    standingTileIds = player.hand.standingTiles.map { it.id }.filterNot { it == winningTileId },
                    melds = player.hand.melds.map { it.toPresentation(currentState.config.revealsClosedKanTiles) },
                    winningTileId = winningTileId,
                    detailFields = resolvedDetails.getValue(winnerId).fields,
                )
            },
            ranking = ScoreRankingPresentation(
                currentState.players.mapIndexed { seatIndex, player ->
                    val previous = previousState.players.first { it.id == player.id }
                    ScoreRankingPlayer(
                        player.id,
                        seatIndex,
                        player.isAi,
                        previous.score,
                        player.score,
                        previousRanks.getValue(player.id),
                        currentRanks.getValue(player.id),
                    )
                },
            ),
        )
    }

    const val GENERIC_TEMPLATE_KEY = "mahjongcraft:generic"
}
