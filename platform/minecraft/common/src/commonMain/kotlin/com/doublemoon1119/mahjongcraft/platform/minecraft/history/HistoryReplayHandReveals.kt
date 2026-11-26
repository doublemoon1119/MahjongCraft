package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayFactDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTransactionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose

/**
 * 牌譜回放中哪幾家攤牌，與對局中的演出相同：
 *
 * - 胡牌：胡牌紀錄中有記下手牌的贏家倒牌，例如自摸、榮和、搶槓。規則決定不攤牌的胡牌不記手牌，不倒。胡牌後這一局繼續時，
 *   已胡的人之後一直倒著。
 * - 流局：附註記下公開的人倒牌，其他人的手牌蓋起來。
 */
object HistoryReplayHandReveals {
    /**
     * 到某一步為止倒牌的贏家。
     *
     * @param transactions 這一局依序的交易。
     * @param step 步數。0 是初始牌面，k 是第 k - 1 筆交易完成後。
     * @return 依初始座位的和牌張。胡牌紀錄沒有和牌張時為 null。
     */
    fun revealedWinners(transactions: List<HistoryReplayTransactionDto>, step: Int): Map<Int, Int?> = buildMap {
        transactions.filter { it.index < step }.forEach { transaction ->
            transaction.facts.forEach { fact ->
                (fact as? HistoryReplayFactDto.Completion)?.outcome?.winnerDetails?.forEach { winner ->
                    val hand = winner.hand ?: return@forEach
                    if (hand.standingTiles.isNotEmpty()) put(winner.seatIndex, hand.winningTile)
                }
            }
        }
    }

    /**
     * 這一步每一家手牌的攤牌方式。
     *
     * @param state 牌面。
     * @param notes 這一步的呈現附註。
     * @param winners 到這一步為止倒牌的贏家，依初始座位，見 [revealedWinners]。
     * @return 依玩家在牌面中的位置。沒有攤牌的玩家不列入。
     */
    fun reveals(state: HistoryRoundStateDto, notes: ReplayTableNotes, winners: Map<Int, Int?>): Map<Int, HandReveal> = buildMap {
        state.players.forEachIndexed { index, player ->
            when {
                player.initialSeatIndex in winners -> put(index, HandReveal.Win(winningTile = winners[player.initialSeatIndex]?.takeIf { it == player.lastDrawn }))
                notes.drawHandReveals[index] == true -> put(index, HandReveal.DrawRevealed)
                notes.drawHandReveals[index] == false -> put(index, HandReveal.DrawConcealed)
            }
        }
    }

    /**
     * 攤牌後手牌的姿態：倒牌的牌面朝上，蓋起來的牌背朝上。
     *
     * @param state 牌面。
     * @param reveals 每一家的攤牌方式，見 [reveals]。
     * @return 依局內牌索引的姿態。
     */
    fun handPoses(state: HistoryRoundStateDto, reveals: Map<Int, HandReveal>): Map<Int, TilePose> = buildMap {
        reveals.forEach { (index, reveal) ->
            val player = state.players[index]
            val pose = if (reveal == HandReveal.DrawConcealed) TilePose.FACE_DOWN else TilePose.FACE_UP
            (player.handTiles + listOfNotNull(player.lastDrawn)).forEach { put(it, pose) }
        }
    }
}
