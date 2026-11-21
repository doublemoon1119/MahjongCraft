package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayFactDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTransactionDto
import kotlin.random.Random

/**
 * 牌譜回放中胡牌特效打在哪幾張牌上，與對局中的演出相同：每一次胡牌在和牌張上放一次特效，不論有沒有攤牌；一炮多響的
 * 贏家共用同一張和牌張，只放一次。
 */
object HistoryReplayWinEffects {
    /**
     * 往後播到某一步時要放特效的和牌張。
     *
     * @param transactions 這一局依序的交易。
     * @param step 播完後的步數；k 是第 k - 1 筆交易完成後。
     * @return 這一步胡牌的和牌張，依局內牌索引；這一步沒有胡牌或胡牌紀錄沒有和牌張時為空。
     */
    fun winningTiles(transactions: List<HistoryReplayTransactionDto>, step: Int): List<Int> = transactions
        .filter { it.index == step - 1 }
        .flatMap { it.facts }
        .mapNotNull { (it as? HistoryReplayFactDto.Completion)?.outcome?.winningTile }
        .distinct()

    /**
     * 一次特效的隨機種子；同一局同一步重看時三叉戟角度與閃電形狀都一樣。
     *
     * @param roundNumber 局序號。
     * @param step 播完後的步數。
     * @param tile 和牌張的局內牌索引。
     * @return 隨機種子。
     */
    fun seed(roundNumber: Int, step: Int, tile: Int): Long = Random(seed = (roundNumber.toLong() shl SEED_ROUND_SHIFT) xor (step.toLong() shl SEED_STEP_SHIFT) xor tile.toLong()).nextLong()

    /** 局序號在種子來源中的位移。 */
    private const val SEED_ROUND_SHIFT = 40

    /** 步數在種子來源中的位移。 */
    private const val SEED_STEP_SHIFT = 16
}
