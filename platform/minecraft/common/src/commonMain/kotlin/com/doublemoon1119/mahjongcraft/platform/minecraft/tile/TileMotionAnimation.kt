package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector
import kotlin.math.PI
import kotlin.math.sin

/**
 * 一次牌張運動動畫的可調整參數；牌牆生成掉落、發牌、摸牌共用同一個 [TileMotionAnimation]，各自傳入
 * 不同的 spec——牌牆生成／發牌起訖姿態相同（[startPoseRotationDegrees] 等於 [endPoseRotationDegrees]，
 * 只有位置在動），摸牌才需要非零的姿態旋轉內插（牌背朝上→直立）。
 */
data class TileMotionAnimationSpec(
    /** 動畫總長度，以 server ticks 表示。 */
    val durationTicks: Int = DEFAULT_DURATION_TICKS,
    /** 拋物線最高額外高度；牌牆生成可設 `0.0` 走純直落，不套用拋物線。 */
    val arcHeight: Double = 0.0,
    /** 起始姿態的局部 X 軸旋轉角，取代靜態的 `tilePose.rotationDegrees`。 */
    val startPoseRotationDegrees: Float,
    /** 結束姿態的局部 X 軸旋轉角。 */
    val endPoseRotationDegrees: Float,
) {
    init {
        require(durationTicks > 0) { "durationTicks must be positive" }
        require(arcHeight >= 0.0) { "arcHeight must not be negative" }
    }

    companion object {
        /**
         * 預設動畫總長度，約為正常 TPS 下的 0.25 秒；牌牆生成掉落動畫經過遊戲內多次比對後持續調快，
         * 從原本 15（0.75 秒）先調到 8（0.4 秒）、再調到現在的值——擲骰子動畫改成等牌牆完全
         * 落地後才開始播放（見 `FabricGamePresentationPublisher.publishDiceRoll`），牌牆這段時間會
         * 直接疊加進開局的總等待時間，比過去兩者平行播放時更值得壓縮。
         */
        const val DEFAULT_DURATION_TICKS = 5
    }
}

/** renderer 在指定時間需要套用的視覺位移與姿態旋轉角。 */
data class TileMotionAnimationFrame(
    /** 相對 entity 最終位置的視覺位移。 */
    val offset: DiceAnimationVector,
    /** 內插後的姿態旋轉角，取代靜態的 `tilePose.rotationDegrees`。 */
    val poseRotationDegrees: Float,
    /** 限制在 `0～1` 的動畫進度。 */
    val progress: Double,
    /** 動畫是否已完成。 */
    val completed: Boolean,
)

/**
 * 由 server tick 與起點偏移產生可重建牌張運動路徑的純計算器；跟 [DiceRollAnimation] 不同，牌張運動
 * 不需要隨機物理，同一組輸入永遠算出同一個 frame，不需要吃 seed。
 */
class TileMotionAnimation(
    /** 此動畫使用的時間與幅度參數。 */
    private val spec: TileMotionAnimationSpec,
) {
    /** 計算指定經過 ticks、指定起點相對終點偏移的視覺 frame。 */
    fun frame(elapsedTicks: Double, startOffset: DiceAnimationVector): TileMotionAnimationFrame {
        val progress = (elapsedTicks / spec.durationTicks).coerceIn(0.0, 1.0)
        if (progress >= 1.0) {
            return TileMotionAnimationFrame(
                offset = DiceAnimationVector.ZERO,
                poseRotationDegrees = spec.endPoseRotationDegrees,
                progress = 1.0,
                completed = true,
            )
        }

        // 位移用 ease-out（1 減去「1 減 progress」的平方）取代原始 progress 直接線性內插——遊戲內
        // 實際比較過先慢後快（ease-in）跟先快後慢（ease-out）兩種曲線，後者落地前明顯減速的感覺更好；
        // 姿態旋轉角刻意維持線性內插，不受此影響（旋轉的速度感不需要跟位移的曲線綁在一起）。
        val remaining = 1.0 - progress
        val easedProgress = 1.0 - remaining * remaining
        val offset = DiceAnimationVector(
            x = startOffset.x * (1.0 - easedProgress),
            y = startOffset.y * (1.0 - easedProgress) + sin(PI * progress) * spec.arcHeight,
            z = startOffset.z * (1.0 - easedProgress),
        )
        val poseRotationDegrees = spec.startPoseRotationDegrees +
            (spec.endPoseRotationDegrees - spec.startPoseRotationDegrees) * progress.toFloat()
        return TileMotionAnimationFrame(
            offset = offset,
            poseRotationDegrees = poseRotationDegrees,
            progress = progress,
            completed = false,
        )
    }
}
