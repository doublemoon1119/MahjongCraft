package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

/** 壓力測試的桌數安排。 */
sealed interface StressTestMode {
    /**
     * 固定維持 [tables] 桌同時進行。
     *
     * @property tables 同時進行的桌數，介於 1 與 [STRESS_MAX_TABLES] 之間。
     */
    data class Fixed(val tables: Int) : StressTestMode {
        init {
            require(tables in 1..STRESS_MAX_TABLES) { "Stress test table count must be between 1 and $STRESS_MAX_TABLES" }
        }
    }

    /**
     * 依 [plan] 逐步增加桌數，直到安全閥停止。
     *
     * @property plan 桌數增加的方式。
     */
    data class Ramp(val plan: StressRampPlan) : StressTestMode
}
