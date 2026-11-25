package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

/**
 * 回放速度。
 *
 * @property multiplier 相對於一倍速的倍數。一倍速時一筆交易約停一秒。
 */
internal enum class HistoryReplaySpeed(val multiplier: Double) {
    /** 0.5 倍速。 */
    HALF(multiplier = 0.5),

    /** 一倍速。 */
    NORMAL(multiplier = 1.0),

    /** 兩倍速。 */
    DOUBLE(multiplier = 2.0),

    /** 四倍速。 */
    QUADRUPLE(multiplier = 4.0),
}
