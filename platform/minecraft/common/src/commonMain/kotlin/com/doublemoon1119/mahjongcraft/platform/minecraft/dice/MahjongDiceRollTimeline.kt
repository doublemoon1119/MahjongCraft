package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

/**
 * 依每輪骰子數量計算平台共用的開局擲骰時間線。
 *
 * 這裡只計算時間，不建立世界物件，也不決定擲骰者。各平台可用同一份結果安排骰子、忙碌狀態與初次
 * 發牌的延遲，避免不同呈現元件各自重算而出現錯位。
 */
object MahjongDiceRollTimeline {
    /**
     * 依骰子數量建立連續的擲骰區段。
     *
     * @param diceCounts 按照規則程序排列的每輪骰子數量；空清單代表沒有擲骰呈現。
     * @param initialDelayTicks 第一輪開始前要保留的前置 tick 數，例如牌牆落地動畫。
     * @return 每輪的開始與結束相對 tick；順序與 [diceCounts] 相同。
     * @throws IllegalArgumentException 前置等待為負數，或某輪使用未支援的骰子數量。
     */
    fun segments(
        diceCounts: List<Int>,
        initialDelayTicks: Int = 0,
    ): List<MahjongDiceRollTimelineSegment> {
        require(initialDelayTicks >= 0) { "Initial delay must not be negative" }
        var cursor = initialDelayTicks
        return diceCounts.map { diceCount ->
            val start = cursor
            cursor += MahjongDiceTableLayout.totalAnimationTicks(diceCount)
            MahjongDiceRollTimelineSegment(startOffsetTicks = start, endOffsetTicks = cursor)
        }
    }

    /**
     * 取得整批擲骰與前置等待所需的總 tick 數。
     *
     * @param diceCounts 按照規則程序排列的每輪骰子數量。
     * @param initialDelayTicks 第一輪開始前的前置 tick 數。
     * @return 最後一輪結果展示結束前經過的 tick 數。
     */
    fun totalTicks(
        diceCounts: List<Int>,
        initialDelayTicks: Int = 0,
    ): Int = segments(diceCounts, initialDelayTicks).lastOrNull()?.endOffsetTicks ?: initialDelayTicks
}
