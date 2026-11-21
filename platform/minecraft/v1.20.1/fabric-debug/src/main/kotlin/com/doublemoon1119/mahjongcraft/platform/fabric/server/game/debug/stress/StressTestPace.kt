package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

/**
 * 壓力測試中每桌推進對局的節奏。
 *
 * @property commandName 指令中使用的名稱。
 * @property stepIntervalTicks 同一桌兩步之間間隔的伺服器 tick 數。
 */
enum class StressTestPace(val commandName: String, val stepIntervalTicks: Int) {
    /** 每桌約每秒一步，模擬真實對局的步調。 */
    REALTIME("realtime", 20),

    /** 每桌每 tick 一步，去掉動畫等待後最快的節奏。 */
    FAST("fast", 1),
}
