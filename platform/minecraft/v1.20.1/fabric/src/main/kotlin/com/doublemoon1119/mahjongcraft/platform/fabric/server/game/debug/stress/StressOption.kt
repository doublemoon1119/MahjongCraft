package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

/**
 * 指令中的具名選項；以「名稱 值」成對出現，順序不限、每個最多一次。
 *
 * @property commandName 指令中使用的名稱。
 * @property range 數值選項接受的範圍；非數值選項為 null。
 */
enum class StressOption(val commandName: String, val range: IntRange?) {
    /** 推進節奏：`realtime` 或 `fast`。 */
    PACE("pace", null),

    /** 歷史處理方式：`write`、`encode` 或 `off`。 */
    HISTORY("history", null),

    /** 判定卡頓的單一 tick 實際間隔毫秒數；沿用 max_mspt 指令名稱。 */
    MAX_MSPT("max_mspt", 1..10_000),

    /** 暖機秒數。 */
    WARMUP("warmup", 0..600),
}
