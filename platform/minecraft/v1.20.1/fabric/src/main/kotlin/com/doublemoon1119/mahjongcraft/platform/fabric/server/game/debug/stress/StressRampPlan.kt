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

    /** 開始後經過 [elapsedTicks] 時應同時進行的桌數。 */
    fun tablesAt(elapsedTicks: Long): Int = initialTables + stepTables * (elapsedTicks.coerceAtLeast(0) / intervalTicks).toInt()

    /**
     * 在 [elapsedTicks] 時停止，已完整撐過一個階段的最大桌數；第一個階段就停止時為 0。
     */
    fun sustainedTablesAt(elapsedTicks: Long): Int = (tablesAt(elapsedTicks) - stepTables).coerceAtLeast(0)
}
