package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/** 麻將骰子朝上的點數及其靜止模型旋轉。 */
enum class MahjongDicePoint(
    /** 骰子顯示值。 */
    val value: Int,
    /** 模型局部 X 軸旋轉角度。 */
    val xRotationDegrees: Float,
    /** 模型局部 Y 軸旋轉角度。 */
    val yRotationDegrees: Float,
) {
    /** 一點朝上。 */
    ONE(1, 0.0f, 0.0f),

    /** 二點朝上。 */
    TWO(2, 90.0f, 0.0f),

    /** 三點朝上。 */
    THREE(3, 90.0f, 90.0f),

    /** 四點朝上。 */
    FOUR(4, 90.0f, -90.0f),

    /** 五點朝上。 */
    FIVE(5, -90.0f, 0.0f),

    /** 六點朝上。 */
    SIX(6, 180.0f, 0.0f),
    ;

    /** 依一至六循環至下一點。 */
    fun next(): MahjongDicePoint = entries[(ordinal + 1) % entries.size]

    companion object {
        /** 由數值取得點數；無效值使用一點。 */
        fun fromValueOrDefault(value: Int): MahjongDicePoint = entries.firstOrNull { it.value == value } ?: ONE
    }
}
