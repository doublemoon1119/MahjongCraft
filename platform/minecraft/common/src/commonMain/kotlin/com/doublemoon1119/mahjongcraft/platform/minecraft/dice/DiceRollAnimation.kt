package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

/**
 * 一次骰子翻滾動畫的可調整參數。
 *
 * @property durationTicks 動畫總長度，以 server ticks 表示。
 * @property startOffsetX 畫面起點相對最終落點的 X 偏移。
 * @property startOffsetY 畫面起點相對最終落點的 Y 偏移。
 * @property startOffsetZ 畫面起點相對最終落點的 Z 偏移。
 * @property tossHeight 拋物線最高額外高度。
 * @property firstBounceHeight 第一次落地後的彈跳高度。
 * @property secondBounceHeight 第二次落地後的彈跳高度。
 */
data class DiceRollAnimationSpec(
    val durationTicks: Int = DEFAULT_DURATION_TICKS,
    val startOffsetX: Double = -0.65,
    val startOffsetY: Double = 0.0,
    val startOffsetZ: Double = 0.0,
    val tossHeight: Double = 0.45,
    val firstBounceHeight: Double = 0.10,
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

    /**
     * 依動畫階段與 [seed] 建立三次桌面接觸的權威聲音提示；使用向上取整確保聲音不會早於畫面接觸。
     *
     * @param seed 用於重建確定性音高差異的動畫 seed。
     * @return 依接觸時間排列的聲音提示。
     */
    fun landingSoundCues(seed: Long): List<DiceLandingSoundCue> {
        val random = DeterministicDiceRandom(seed xor SOUND_PITCH_SEED)
        return LANDING_PROGRESS.zip(LANDING_VOLUMES).map { (progress, volume) ->
            DiceLandingSoundCue(
                tickOffset = ceil(durationTicks * progress).toInt(),
                volume = volume,
                pitch = (MIN_LANDING_PITCH + random.nextDouble() * LANDING_PITCH_VARIATION).toFloat(),
            )
        }
    }

    companion object {
        /** 預設動畫總長度，約為正常 TPS 下的 1.5 秒。 */
        const val DEFAULT_DURATION_TICKS = 30

        /**
         * 最後一顆骰子停穩後顯示聚合結果面板的 tick 數（2 秒 @ 20 TPS）——單一來源，供桌面骰子共同
         * 消失時間、結果 presentation 與後續發牌延遲共用，避免三條時間線各自維護不同常數。
         */
        const val EXTRA_VIEWING_TICKS = 40

        /** 三次桌面接觸在完整動畫中的標準化時間。 */
        private val LANDING_PROGRESS: List<Double> = listOf(0.55, 0.78, 0.93)

        /** 三次桌面接觸依序遞減的播放音量。 */
        private val LANDING_VOLUMES: List<Float> = listOf(1.0f, 0.55f, 0.30f)

        /** 骰子桌面接觸的最低音高。 */
        private const val MIN_LANDING_PITCH: Double = 0.97

        /** 骰子桌面接觸可由 seed 改變的音高範圍。 */
        private const val LANDING_PITCH_VARIATION: Double = 0.06

        /** 將聲音音高亂數與動畫路徑亂數分離的固定 salt。 */
        private const val SOUND_PITCH_SEED: Long = 0x534F554E44435545L
    }
}

/**
 * 由 seed 與 server tick 產生可重建骰子翻滾 frame 的純計算器。
 *
 * @property spec 此動畫使用的時間與幅度參數。
 */
class DiceRollAnimation(
    private val spec: DiceRollAnimationSpec = DiceRollAnimationSpec(),
) {
    /**
     * 計算指定經過 ticks 的視覺 frame。
     *
     * @param seed 決定此動畫的固定隨機路徑。
     * @param elapsedTicks 動畫已經過的 tick 數，可包含小數部分。
     * @param startOffset 畫面起點相對最終落點的偏移；預設取自 [spec]。
     * @return 指定時間的位移、旋轉、進度與完成狀態。
     */
    fun frame(
        seed: Long,
        elapsedTicks: Double,
        startOffset: DiceAnimationVector = DiceAnimationVector(
            spec.startOffsetX,
            spec.startOffsetY,
            spec.startOffsetZ,
        ),
    ): DiceRollAnimationFrame {
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
            offset = calculateOffset(progress, lateralDirection, startOffset),
            rotation = calculateRotation(progress, spinAxis, spinTurns),
            progress = progress,
            completed = false,
        )
    }

    /**
     * 計算拋出、兩次彈跳及最後靜止階段的位移。
     *
     * @param progress 動畫進度。
     * @param lateralDirection seed 產生的側向方向係數。
     * @param startOffset 畫面起點偏移。
     * @return 此階段的視覺位移。
     */
    private fun calculateOffset(
        progress: Double,
        lateralDirection: Double,
        startOffset: DiceAnimationVector,
    ): DiceAnimationVector = when {
        progress < FLIGHT_END -> {
            val local = progress / FLIGHT_END
            DiceAnimationVector(
                x = startOffset.x * (1.0 - local),
                y = startOffset.y * (1.0 - local) + sin(PI * local) * spec.tossHeight,
                z = startOffset.z * (1.0 - local) + sin(PI * local) * LATERAL_OFFSET * lateralDirection,
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

    /**
     * 計算持續翻滾並於結尾收斂為 identity 的額外旋轉。
     *
     * @param progress 動畫進度。
     * @param spinAxis 此次動畫的旋轉軸。
     * @param spinTurns 此次動畫的總旋轉圈數。
     * @return 此進度的額外旋轉。
     */
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

    /**
     * 計算指定階段的正弦彈跳高度。
     *
     * @param progress 全動畫進度。
     * @param start 階段開始進度。
     * @param end 階段結束進度。
     * @param height 此階段的最高高度。
     * @return 此進度的彈跳高度。
     */
    private fun bounceHeight(progress: Double, start: Double, end: Double, height: Double): Double = sin(
        PI * ((progress - start) / (end - start)),
    ) * height

    /**
     * 讓最終旋轉收斂的速度在兩端平滑。
     *
     * @param progress 收斂進度；計算前會限制在 0 到 1。
     * @return 平滑後的進度。
     */
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

/**
 * 跨 Kotlin target 產生相同序列的 SplitMix64 亂數。
 *
 * @param seed 亂數序列的初始狀態。
 */
private class DeterministicDiceRandom(seed: Long) {
    /** 目前內部狀態。 */
    private var state: Long = seed

    /** 產生大於等於 0、小於 1 的下一個值。 */
    fun nextDouble(): Double = (nextLong().ushr(11).toDouble() / DOUBLE_UNIT_DENOMINATOR)

    /** 產生大於等於 -1、小於 1 的下一個值。 */
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
