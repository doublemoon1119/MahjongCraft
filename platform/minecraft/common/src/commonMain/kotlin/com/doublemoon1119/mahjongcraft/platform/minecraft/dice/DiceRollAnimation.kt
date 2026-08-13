package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

import kotlin.math.PI
import kotlin.math.sin

/** 一次骰子翻滾動畫的可調整參數。 */
data class DiceRollAnimationSpec(
    /** 動畫總長度，以 server ticks 表示。 */
    val durationTicks: Int = 30,
    /** 畫面起點相對最終落點的 X 偏移。 */
    val startOffsetX: Double = -0.65,
    /** 畫面起點相對最終落點的 Y 偏移。 */
    val startOffsetY: Double = 0.0,
    /** 畫面起點相對最終落點的 Z 偏移。 */
    val startOffsetZ: Double = 0.0,
    /** 拋物線最高額外高度。 */
    val tossHeight: Double = 0.45,
    /** 第一次落地後的彈跳高度。 */
    val firstBounceHeight: Double = 0.10,
    /** 第二次落地後的彈跳高度。 */
    val secondBounceHeight: Double = 0.035,
) {
    init {
        require(durationTicks > 0) { "durationTicks must be positive" }
        require(tossHeight >= 0.0) { "tossHeight must not be negative" }
        require(firstBounceHeight >= 0.0) { "firstBounceHeight must not be negative" }
        require(secondBounceHeight in 0.0..firstBounceHeight) {
            "secondBounceHeight must be between zero and firstBounceHeight"
        }
    }
}

/** renderer 在指定時間需要套用的視覺位移與額外旋轉。 */
data class DiceRollAnimationFrame(
    /** 相對 entity 最終位置的視覺位移。 */
    val offset: DiceAnimationVector,
    /** 在最終點數姿態之前套用的額外旋轉。 */
    val rotation: DiceAnimationQuaternion,
    /** 限制在 `0～1` 的動畫進度。 */
    val progress: Double,
    /** 動畫是否已完成。 */
    val completed: Boolean,
)

/** 由 seed 與 server tick 產生可重建骰子翻滾 frame 的純計算器。 */
class DiceRollAnimation(
    /** 此動畫使用的時間與幅度參數。 */
    private val spec: DiceRollAnimationSpec = DiceRollAnimationSpec(),
) {
    /** 計算指定經過 ticks 的視覺 frame。 */
    fun frame(seed: Long, elapsedTicks: Double): DiceRollAnimationFrame {
        val progress = (elapsedTicks / spec.durationTicks).coerceIn(0.0, 1.0)
        if (progress >= 1.0) {
            return DiceRollAnimationFrame(
                offset = DiceAnimationVector.ZERO,
                rotation = DiceAnimationQuaternion.IDENTITY,
                progress = 1.0,
                completed = true,
            )
        }

        val random = DeterministicDiceRandom(seed)
        val lateralDirection = random.nextSignedDouble()
        val spinAxis = DiceAnimationVector(
            x = random.nextSignedDouble(),
            y = 0.35 + random.nextDouble(),
            z = random.nextSignedDouble(),
        ).normalized()
        val spinTurns = MIN_SPIN_TURNS + random.nextDouble() * SPIN_TURN_VARIATION
        return DiceRollAnimationFrame(
            offset = calculateOffset(progress, lateralDirection),
            rotation = calculateRotation(progress, spinAxis, spinTurns),
            progress = progress,
            completed = false,
        )
    }

    /** 計算拋出、兩次彈跳及最後靜止階段的位移。 */
    private fun calculateOffset(progress: Double, lateralDirection: Double): DiceAnimationVector = when {
        progress < FLIGHT_END -> {
            val local = progress / FLIGHT_END
            DiceAnimationVector(
                x = spec.startOffsetX * (1.0 - local),
                y = spec.startOffsetY * (1.0 - local) + sin(PI * local) * spec.tossHeight,
                z = spec.startOffsetZ * (1.0 - local) + sin(PI * local) * LATERAL_OFFSET * lateralDirection,
            )
        }
        progress < FIRST_BOUNCE_END -> DiceAnimationVector(
            x = 0.0,
            y = bounceHeight(progress, FLIGHT_END, FIRST_BOUNCE_END, spec.firstBounceHeight),
            z = 0.0,
        )
        progress < SECOND_BOUNCE_END -> DiceAnimationVector(
            x = 0.0,
            y = bounceHeight(progress, FIRST_BOUNCE_END, SECOND_BOUNCE_END, spec.secondBounceHeight),
            z = 0.0,
        )
        else -> DiceAnimationVector.ZERO
    }

    /** 計算持續翻滾並於結尾收斂為 identity 的額外旋轉。 */
    private fun calculateRotation(
        progress: Double,
        spinAxis: DiceAnimationVector,
        spinTurns: Double,
    ): DiceAnimationQuaternion {
        val settleStartRotation = DiceAnimationQuaternion.fromAxisAngle(
            spinAxis,
            spinTurns * FULL_ROTATION_RADIANS * ROTATION_SETTLE_START,
        )
        if (progress >= ROTATION_SETTLE_START) {
            val settleProgress = (progress - ROTATION_SETTLE_START) / (1.0 - ROTATION_SETTLE_START)
            return settleStartRotation.slerp(DiceAnimationQuaternion.IDENTITY, smoothStep(settleProgress))
        }
        return DiceAnimationQuaternion.fromAxisAngle(
            spinAxis,
            spinTurns * FULL_ROTATION_RADIANS * progress,
        )
    }

    /** 計算指定階段的正弦彈跳高度。 */
    private fun bounceHeight(progress: Double, start: Double, end: Double, height: Double): Double = sin(
        PI * ((progress - start) / (end - start)),
    ) * height

    /** 讓最終旋轉收斂的速度在兩端平滑。 */
    private fun smoothStep(progress: Double): Double {
        val clamped = progress.coerceIn(0.0, 1.0)
        return clamped * clamped * (3.0 - 2.0 * clamped)
    }

    private companion object {
        /** 拋物線落地的標準化時間。 */
        const val FLIGHT_END = 0.55

        /** 第一次彈跳結束的標準化時間。 */
        const val FIRST_BOUNCE_END = 0.78

        /** 第二次彈跳結束的標準化時間。 */
        const val SECOND_BOUNCE_END = 0.93

        /** 額外旋轉開始收斂的標準化時間。 */
        const val ROTATION_SETTLE_START = 0.82

        /** seed 可產生的最大側向弧線幅度。 */
        const val LATERAL_OFFSET = 0.12

        /** 動畫最少旋轉圈數。 */
        const val MIN_SPIN_TURNS = 3.0

        /** seed 額外增加的旋轉圈數範圍。 */
        const val SPIN_TURN_VARIATION = 2.0

        /** 一整圈的弧度。 */
        const val FULL_ROTATION_RADIANS = 2.0 * PI
    }
}

/** 跨 Kotlin target 產生相同序列的 SplitMix64 亂數。 */
private class DeterministicDiceRandom(seed: Long) {
    /** 目前內部狀態。 */
    private var state: Long = seed

    /** 產生 `0.0..1.0` 的下一個值。 */
    fun nextDouble(): Double = (nextLong().ushr(11).toDouble() / DOUBLE_UNIT_DENOMINATOR)

    /** 產生 `-1.0..1.0` 的下一個值。 */
    fun nextSignedDouble(): Double = nextDouble() * 2.0 - 1.0

    /** 產生下一個 64-bit 雜湊值。 */
    private fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var value = state
        value = (value xor (value ushr 30)) * MIX_MULTIPLIER_ONE
        value = (value xor (value ushr 27)) * MIX_MULTIPLIER_TWO
        return value xor (value ushr 31)
    }

    private companion object {
        /** SplitMix64 state 步進值。 */
        const val GOLDEN_GAMMA = -7046029254386353131L

        /** SplitMix64 第一個混合乘數。 */
        const val MIX_MULTIPLIER_ONE = -4658895280553007687L

        /** SplitMix64 第二個混合乘數。 */
        const val MIX_MULTIPLIER_TWO = -7723592293110705685L

        /** 將 53 個有效 bits 轉成 Double 單位區間的分母。 */
        const val DOUBLE_UNIT_DENOMINATOR = 9007199254740992.0
    }
}
