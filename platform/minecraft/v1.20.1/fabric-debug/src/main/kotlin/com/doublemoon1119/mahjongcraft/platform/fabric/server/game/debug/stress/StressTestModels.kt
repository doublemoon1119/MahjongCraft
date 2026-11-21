package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

/** 同時進行桌數的上限；固定桌數的指令引數與爬坡的目標桌數都不會超過。 */
const val STRESS_MAX_TABLES: Int = 1_000

/** 每個 tick 最多新建的桌數；需要大量建桌時分散到連續幾個 tick，避免安全閥介入前單一 tick 就卡住。 */
const val STRESS_TABLES_CREATED_PER_TICK: Int = 4

/** 統計「每 tick 耗時超過門檻」比例的門檻毫秒數。 */
val STRESS_SLOW_TICK_THRESHOLDS_MILLIS: List<Int> = listOf(50, 100, 250)
