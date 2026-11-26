package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

/**
 * 已由伺服器決定的單顆正式骰子資料。
 *
 * @property point 最終朝上的點數。
 * @property animationSeed 確定性動畫使用的 seed。
 */
data class MahjongDicePresentation(
    val point: Int,
    val animationSeed: Long,
) {
    init {
        require(point in VALID_POINT_RANGE) { "Dice point must be between 1 and 6" }
    }

    /** 骰子點數合法範圍。 */
    private companion object {
        val VALID_POINT_RANGE: IntRange = 1..6
    }
}
