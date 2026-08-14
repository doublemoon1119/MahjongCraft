package com.doublemoon1119.mahjongcraft.logic.table.opening

/**
 * 一次伺服器權威擲骰的個別點數。
 *
 * 保留每顆骰子的值，讓規則計算、平台呈現與 persistence 使用同一份結果；不得只保存總和後再由
 * Minecraft 或其他平台反推個別點數。
 *
 * @property values 依擲骰結果順序排列的點數，每個值皆位於 `1..6`，且至少包含一顆骰子。
 */
@JvmInline
value class DiceRollResult private constructor(
    val values: List<Int>,
) {
    /** 全部骰子的點數總和。 */
    val total: Int get() = values.sum()

    companion object {
        /**
         * 建立不可受呼叫端後續修改影響的擲骰結果。
         *
         * @param values 個別骰子的點數。
         * @return 保存 [values] 副本的擲骰結果。
         * @throws IllegalArgumentException 當沒有骰子或任一點數不在 `1..6` 時拋出。
         */
        fun of(values: List<Int>): DiceRollResult {
            require(values.isNotEmpty()) { "Dice roll must contain at least one die" }
            require(values.all { it in 1..6 }) { "Every die value must be in 1..6" }
            return DiceRollResult(values.toList())
        }
    }
}
