package com.doublemoon1119.mahjongcraft.example.pairing

/**
 * 本局固定的加分牌點數，不包含對局或玩家的其他狀態。
 *
 * @property values 已選出的萬子點數；同一局內只讀取已保存的結果。
 */
data class ExamplePairingBonusTiles(val values: Set<Int>) {
    init {
        require(values.isNotEmpty()) { "Bonus tile values must not be empty" }
        require(values.all { it in 1..8 }) { "Bonus tile values must be between 1 and 8" }
    }
}
