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
                // 本局已經胡牌退場的玩家（見 TableState.finishedPlayerIds）只保留分數排行的那一列：
                // 手牌在他胡牌時就已經收尾蓋好了，這裡不能再排一次動畫，也不該被標成聽牌／不聽——
                // 他根本沒有參與這次流局。
                val isFinished = player.id in currentState.finishedPlayerIds
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
                    // 只取立牌：副露是公開資訊，蓋起來既不合規則，也會摧毀既有的牌面、橫置方向與
                    // 加槓疊牌版面（這個欄位的契約本來就寫明不含副露）。
                    handTileIds = if (isFinished) emptyList() else player.hand.standingTiles.map { it.id },
                    handPresentation = handPresentation,
                    revealedHandTileIds = if (handPresentation == ExhaustiveDrawSettlementHandPresentation.CONCEAL) emptyList() else player.hand.allTiles.map { it.id },
                    waitingTiles = reveal?.waitingTiles.orEmpty().toList(),
                    statusId = when {
                        isFinished -> null
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
