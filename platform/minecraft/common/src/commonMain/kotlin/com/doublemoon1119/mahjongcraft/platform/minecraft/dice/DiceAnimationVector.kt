package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

import kotlin.math.sqrt

/** 不依賴 Minecraft API 的骰子動畫三維向量。 */
data class DiceAnimationVector(
    /** X 軸分量。 */
    val x: Double,
    /** Y 軸分量。 */
    val y: Double,
    /** Z 軸分量。 */
    val z: Double,
) {
    /** 向量長度。 */
    val length: Double
        get() = sqrt(x * x + y * y + z * z)

    /** 將向量正規化；零向量固定回傳 X 軸單位向量。 */
    fun normalized(): DiceAnimationVector = if (length == 0.0) {
        UNIT_X
    } else {
        DiceAnimationVector(x / length, y / length, z / length)
    }

    companion object {
        /** 零位移。 */
        val ZERO: DiceAnimationVector = DiceAnimationVector(0.0, 0.0, 0.0)

        /** X 軸單位向量。 */
        val UNIT_X: DiceAnimationVector = DiceAnimationVector(1.0, 0.0, 0.0)
    }
}
