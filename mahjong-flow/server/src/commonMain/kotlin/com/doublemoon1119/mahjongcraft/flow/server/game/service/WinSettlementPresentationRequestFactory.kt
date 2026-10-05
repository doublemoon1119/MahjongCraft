package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementWinnerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 建立胡牌詳情與共用分數排行的權威快照。 */
object WinSettlementPresentationRequestFactory {
    /**
     * 建立不偽造翻符或胡牌張的 win-equivalent 特殊 outcome request；規則專屬欄位交由 [detailResolverRegistry] 解析，
     * [aiPlayerIds] 為由 AI 操控的玩家。
     */
    fun createSpecialOutcome(
        previousState: TableState,
        outcome: ResolvedRoundOutcome,
        module: MahjongRuleModule<*>,
        aiPlayerIds: Set<Uuid>,
        detailResolverRegistry: WinSettlementDetailResolverRegistry,
    ): WinSettlementPresentationRequest {
        val currentState = outcome.settledTableState
        val previousRanks = roundRanksByPlayer(previousState, module)
        val currentRanks = roundRanksByPlayer(currentState, module)
        val detailFields = detailResolverRegistry.resolveSpecialOutcome(module.id, currentState, outcome)
        return WinSettlementPresentationRequest(
            outcomeId = outcome.id,
            ruleModuleId = module.id,
            isTsumo = outcome.responsiblePlayerIds.isEmpty(),
            winners = outcome.beneficiaryPlayerIds.map { winnerId ->
                val player = currentState.players.first { it.id == winnerId }
                WinSettlementWinnerPresentation(
                    playerId = winnerId,
                    seatIndex = currentState.players.indexOf(player),
                    responsiblePlayerId = outcome.responsiblePlayerIds.singleOrNull(),
                    totalScore = outcome.scoreDeltas.getValue(winnerId),
                    standingTileIds = sortedStandingTileIds(player.hand, module),
                    melds = player.hand.melds.map { it.toPresentation(currentState.config.revealsClosedKanTiles, module.tileOrder) },
                    winningTileId = null,
                    detailFields = detailFields,
                )
            },
            ranking = ScoreRankingPresentation(
                currentState.players.mapIndexed { seatIndex, player ->
                    val previous = previousState.players.first { it.id == player.id }
                    ScoreRankingPlayer(
                        player.id,
                        seatIndex,
                        player.id in aiPlayerIds,
                        previous.score,
                        player.score,
                        previousRanks.getValue(player.id),
                        currentRanks.getValue(player.id),
                    )
                },
            ),
        )
    }

    /** 建立一般自摸／榮和 request；規則專屬欄位交由 [detailResolverRegistry] 解析，[aiPlayerIds] 為由 AI 操控的玩家。 */
    fun create(
        previousState: TableState,
        currentState: TableState,
        module: MahjongRuleModule<*>,
        aiPlayerIds: Set<Uuid>,
        outcomeId: String,
        isTsumo: Boolean,
        winningTileId: Uuid,
        responsiblePlayerId: Uuid?,
        resolutions: Map<Uuid, WinResolutionResult>,
        detailResolverRegistry: WinSettlementDetailResolverRegistry,
    ): WinSettlementPresentationRequest {
        val previousRanks = roundRanksByPlayer(previousState, module)
        val currentRanks = roundRanksByPlayer(currentState, module)
        val detailFields = resolutions.mapValues { (_, resolution) ->
            detailResolverRegistry.resolve(module.id, currentState, resolution.handValueResult)
        }
        return WinSettlementPresentationRequest(
            outcomeId = outcomeId,
            ruleModuleId = module.id,
            isTsumo = isTsumo,
            winners = resolutions.map { (winnerId, resolution) ->
                val player = currentState.players.first { it.id == winnerId }
                WinSettlementWinnerPresentation(
                    playerId = winnerId,
                    seatIndex = currentState.players.indexOf(player),
                    responsiblePlayerId = responsiblePlayerId,
                    totalScore = resolution.totalGained,
                    standingTileIds = sortedStandingTileIds(player.hand, module).filterNot { it == winningTileId },
                    melds = player.hand.melds.map { it.toPresentation(currentState.config.revealsClosedKanTiles, module.tileOrder) },
                    winningTileId = winningTileId,
                    detailFields = detailFields.getValue(winnerId),
                )
            },
            ranking = ScoreRankingPresentation(
                currentState.players.mapIndexed { seatIndex, player ->
                    val previous = previousState.players.first { it.id == player.id }
                    ScoreRankingPlayer(
                        player.id,
                        seatIndex,
                        player.id in aiPlayerIds,
                        previous.score,
                        player.score,
                        previousRanks.getValue(player.id),
                        currentRanks.getValue(player.id),
                    )
                },
            ),
            paymentReasonIdsByPlayerId = mergePaymentReasons(currentState, responsiblePlayerId, resolutions),
        )
    }

    /**
     * 依規則牌序建立立牌 ID 快照，不改動權威手牌順序或摸牌張。
     *
     * @param hand 含立牌及獨立摸牌張的權威手牌。
     * @param module 提供牌序的規則模組。
     * @return 依規則牌序排列的立牌 ID；不包含副露牌。
     */
    private fun sortedStandingTileIds(hand: Hand, module: MahjongRuleModule<*>): List<Uuid> = hand.standingTiles
        .sortedWith(compareBy(module.tileOrder) { it.tile })
        .map { it.id }

    /**
     * 合併各贏家結算給出的付款原因。
     *
     * 同一位玩家被多位贏家標上不同原因時，取頭跳順位較前（從 [responsiblePlayerId] 起算、座位順序最先
     * 輪到）的贏家給出的原因；沒有 [responsiblePlayerId] 時依 [resolutions] 的順序。
     */
    internal fun mergePaymentReasons(
        state: TableState,
        responsiblePlayerId: Uuid?,
        resolutions: Map<Uuid, WinResolutionResult>,
    ): Map<Uuid, String> {
        val winnersInPriority = responsiblePlayerId?.let { fromId ->
            val fromSeat = state.players.indexOfFirst { it.id == fromId }
            val seatCount = state.players.size
            resolutions.keys.sortedBy { winnerId -> (state.players.indexOfFirst { it.id == winnerId } - fromSeat + seatCount) % seatCount }
        } ?: resolutions.keys.toList()
        val reasons = linkedMapOf<Uuid, String>()
        winnersInPriority.forEach { winnerId ->
            resolutions.getValue(winnerId).settlement.paymentReasonIdsByPlayerId.forEach { (playerId, reasonId) ->
                reasons.putIfAbsent(playerId, reasonId)
            }
        }
        return reasons
    }
}
