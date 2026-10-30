package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryTileReference
import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentViewPersistenceRegistry
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose

/**
 * 回放讀取的受限上下文，驗證局內牌與座位索引並扣除工作預算。
 *
 * @property budget 本次讀取的共用工作預算。
 * @property tileCount 截至目前位置已宣告的實體牌數。
 * @property seatCount header 玩家數。
 * @property componentViews 牌面特徵的保存轉換。
 * @property poses 依牌索引保存的共同姿態；沒有紀錄的牌不列入。
 */
internal class HistoryReplayProjectionContext(
    private val budget: ReplayReadBudget,
    private val tileCount: Int,
    private val seatCount: Int,
    val componentViews: ComponentViewPersistenceRegistry,
    val poses: Map<Int, TilePose> = emptyMap(),
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
