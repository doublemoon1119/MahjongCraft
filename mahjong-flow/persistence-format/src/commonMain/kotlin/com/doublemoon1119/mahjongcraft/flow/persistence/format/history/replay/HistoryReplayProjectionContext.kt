package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryTileReference

/**
 * 擴充解碼器的受限上下文；不能存取或修改權威遊戲。
 *
 * @property budget 本次讀取的共用工作預算。
 * @property tileCount 截至目前位置已宣告的實體牌數。
 * @property seatCount header 玩家數。
 */
class HistoryReplayProjectionContext internal constructor(
    private val budget: ReplayReadBudget,
    private val tileCount: Int,
    private val seatCount: Int,
) {
    /**
     * 建立同局實體牌參照。
     *
     * @param index 局內實體牌索引，不是牌種字典索引。
     * @return 已驗證參照。
     */
    fun tile(index: Int): HistoryTileReference = HistoryTileReference(budget.tile(index, tileCount))

    /**
     * 驗證 header 初始座位索引。
     *
     * @param index 初始座位。
     * @return 原座位。
     */
    fun seat(index: Int): Int = budget.seat(index, seatCount)

    /**
     * 在配置集合或走訪 payload 前扣除工作預算。
     *
     * @param units 工作單位數。
     */
    fun charge(units: Long = 1L) = budget.charge(units)
}
