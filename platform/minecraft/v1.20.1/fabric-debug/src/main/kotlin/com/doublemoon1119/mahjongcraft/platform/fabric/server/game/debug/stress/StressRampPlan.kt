package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

/**
 * 爬坡模式的桌數增加方式。
 *
 * @property initialTables 開始時的桌數。
 * @property stepTables 每次增加的桌數。
 * @property intervalTicks 每次增加之間的伺服器 tick 數。
 */
data class StressRampPlan(
    val initialTables: Int = 4,
    val stepTables: Int = 4,
    val intervalTicks: Long = 600,
) {
    init {
        require(initialTables > 0) { "Initial table count must be positive" }
        require(stepTables > 0) { "Table step must be positive" }
        require(intervalTicks > 0) { "Ramp interval must be positive" }
    }

    /**
     * 計算目前階段應同時進行的桌數。
     *
     * @param elapsedTicks 開始後經過的伺服器 tick 數；負值視為零。
     * @return 起始桌數加上完整經過階段的增量，不在此限制總桌數。
     */
    fun tablesAt(elapsedTicks: Long): Int = initialTables + stepTables * (elapsedTicks.coerceAtLeast(0) / intervalTicks).toInt()

    /**
     * 以目前階段桌數減去一次增量，估計上一階段已維持的桌數。
     * 起始桌數等於每次增量時，第一階段的回報為零；其他組合仍使用同一公式。
     *
     * @param elapsedTicks 開始後經過的伺服器 tick 數。
     * @return 目前計算桌數減去一個增量後，最低為零的數值。
     */
    fun sustainedTablesAt(elapsedTicks: Long): Int = (tablesAt(elapsedTicks) - stepTables).coerceAtLeast(0)
}
