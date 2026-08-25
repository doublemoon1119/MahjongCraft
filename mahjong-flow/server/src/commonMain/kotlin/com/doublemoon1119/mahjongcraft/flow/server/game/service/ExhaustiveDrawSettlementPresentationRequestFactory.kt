package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInExhaustiveDrawSettlementStatusIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementHandPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPlayerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 建立規則中立的流局結算呈現關鍵影格。 */
object ExhaustiveDrawSettlementPresentationRequestFactory {
    /**
     * 依結算前後桌況及規則公開結果建立 request。
     *
     * [tenpaiPlayerIds] 為 null 代表途中流局，不顯示一般荒牌流局的聽牌／未聽狀態。
     */
    fun create(
        previousState: TableState,
        currentState: TableState,
        module: MahjongRuleModule<*>,
        reason: ExhaustiveDrawReason,
        tenpaiPlayerIds: Set<Uuid>?,
        revealedHands: List<RevealedHandSettlement>,
    ): ExhaustiveDrawSettlementPresentationRequest {
        val previousRanks = ranksByPlayer(previousState, module)
        val currentRanks = ranksByPlayer(currentState, module)
        val revealedByPlayer = revealedHands.associateBy { it.playerId }

        return ExhaustiveDrawSettlementPresentationRequest(
            reasonId = reason.id,
            players = currentState.players.mapIndexed { seatIndex, player ->
                val previousPlayer = previousState.players.first { it.id == player.id }
                val reveal = revealedByPlayer[player.id]
                val handPresentation = when {
                    reveal == null -> ExhaustiveDrawSettlementHandPresentation.CONCEAL
                    tenpaiPlayerIds != null && player.id in tenpaiPlayerIds -> ExhaustiveDrawSettlementHandPresentation.REVEAL_TENPAI
                    else -> ExhaustiveDrawSettlementHandPresentation.REVEAL_PROOF
                }
                ExhaustiveDrawSettlementPlayerPresentation(
                    ranking = ScoreRankingPlayer(
                        playerId = player.id,
                        seatIndex = seatIndex,
                        isAi = player.isAi,
                        previousScore = previousPlayer.score,
                        currentScore = player.score,
                        previousRank = previousRanks.getValue(player.id),
                        currentRank = currentRanks.getValue(player.id),
                    ),
                    currentWind = player.currentWind,
                    handTileIds = player.hand.allTiles.map { it.id },
                    handPresentation = handPresentation,
                    revealedHandTileIds = if (handPresentation == ExhaustiveDrawSettlementHandPresentation.CONCEAL) emptyList() else player.hand.allTiles.map { it.id },
                    waitingTiles = reveal?.waitingTiles.orEmpty().toList(),
                    statusId = when {
                        tenpaiPlayerIds != null && player.id in tenpaiPlayerIds && reveal != null ->
                            BuiltInExhaustiveDrawSettlementStatusIds.TENPAI
                        tenpaiPlayerIds != null && player.id !in tenpaiPlayerIds ->
                            BuiltInExhaustiveDrawSettlementStatusIds.NOTEN
                        reveal != null -> BuiltInExhaustiveDrawSettlementStatusIds.DRAW_DECLARATION
                        else -> null
                    },
                )
            },
        )
    }

    /** 依規則的回合排名比較器建立從一開始的名次 map。 */
    private fun ranksByPlayer(state: TableState, module: MahjongRuleModule<*>): Map<Uuid, Int> = state.players.sortedWith(module.compareForRoundRanking()).withIndex().associate { (index, player) ->
        player.id to index + 1
    }
}
