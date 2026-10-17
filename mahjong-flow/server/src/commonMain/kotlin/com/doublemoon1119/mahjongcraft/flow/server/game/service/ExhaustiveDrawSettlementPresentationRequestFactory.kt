package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInExhaustiveDrawSettlementStatusIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawHandDisclosure
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPlayerPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.roundRanksByPlayer
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.PublicHandOrder
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 建立規則中立的流局結算資料。 */
object ExhaustiveDrawSettlementPresentationRequestFactory {
    /**
     * 依結算前後桌況及規則公開結果建立 request。
     *
     * [tenpaiPlayerIds] 為 null 代表途中流局，不顯示一般荒牌流局的聽牌／未聽狀態；[aiPlayerIds] 為由 AI 操控的玩家。
     *
     * @param previousState 結算前的權威桌況。
     * @param currentState 結算後的權威桌況。
     * @param module 本局使用的規則模組，提供牌序及排行規則。
     * @param aiPlayerIds 由 AI 操控的玩家識別碼。
     * @param reason 本次流局原因。
     * @param tenpaiPlayerIds 一般荒牌流局的聽牌玩家；途中流局時為 null。
     * @param revealedHands 規則判定可公開的手牌結算資料。
     * @return 供後續發布的流局結算資料。
     */
    fun create(
        previousState: TableState,
        currentState: TableState,
        module: MahjongRuleModule<*>,
        aiPlayerIds: Set<Uuid>,
        reason: ExhaustiveDrawReason,
        tenpaiPlayerIds: Set<Uuid>?,
        revealedHands: List<RevealedHandSettlement>,
    ): ExhaustiveDrawSettlementPresentationRequest {
        val previousRanks = roundRanksByPlayer(previousState, module)
        val currentRanks = roundRanksByPlayer(currentState, module)
        val revealedByPlayer = revealedHands.associateBy { it.playerId }

        return ExhaustiveDrawSettlementPresentationRequest(
            reasonId = reason.id,
            players = currentState.players.mapIndexed { seatIndex, player ->
                val previousPlayer = previousState.players.first { it.id == player.id }
                val reveal = revealedByPlayer[player.id]
                val revealedTileIds = player.hand.allTiles
                    .map { it.id }
                    .filter { it in currentState.revealedHandTileIds }
                val standingTileIds = player.hand.standingTiles.map { it.id }
                val fullyRevealedStandingHand = standingTileIds.all { it in currentState.revealedHandTileIds }
                // 本局已經胡牌退場的玩家（見 TableState.finishedPlayerIds）只保留分數排行的那一列：
                // 手牌在他胡牌時就已經收尾，這裡不能再要求公開一次，也不該被標成聽牌／不聽——
                // 他根本沒有參與這次流局。
                val isFinished = player.id in currentState.finishedPlayerIds
                val handDisclosure = when {
                    isFinished -> ExhaustiveDrawHandDisclosure.CONCEALED
                    reveal == null || revealedTileIds.isEmpty() -> ExhaustiveDrawHandDisclosure.CONCEALED
                    tenpaiPlayerIds != null && player.id in tenpaiPlayerIds -> ExhaustiveDrawHandDisclosure.TENPAI
                    else -> ExhaustiveDrawHandDisclosure.PROOF
                }
                ExhaustiveDrawSettlementPlayerPresentation(
                    ranking = ScoreRankingPlayer(
                        playerId = player.id,
                        seatIndex = seatIndex,
                        isAi = player.id in aiPlayerIds,
                        previousScore = previousPlayer.score,
                        currentScore = player.score,
                        previousRank = previousRanks.getValue(player.id),
                        currentRank = currentRanks.getValue(player.id),
                    ),
                    seatWind = player.seatWind,
                    // 只取立牌：副露是公開資訊，蓋起來既不合規則，也會摧毀既有的牌面、橫置方向與
                    // 加槓疊牌版面（這個欄位的契約本來就寫明不含副露）。
                    handTileIds = if (isFinished) {
                        emptyList()
                    } else if (!fullyRevealedStandingHand) {
                        PublicHandOrder.arrange(standingTileIds)
                    } else {
                        player.hand.standingTiles
                            .sortedWith(compareBy(module.tileOrder) { it.tile })
                            .map { it.id }
                    },
                    handDisclosure = handDisclosure,
                    revealedHandTileIds = if (handDisclosure == ExhaustiveDrawHandDisclosure.CONCEALED) {
                        emptyList()
                    } else {
                        revealedTileIds
                    },
                    waitingTiles = if (handDisclosure == ExhaustiveDrawHandDisclosure.CONCEALED) {
                        emptyList()
                    } else {
                        reveal?.waitingTiles.orEmpty().sortedWith(module.tileOrder)
                    },
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
}
