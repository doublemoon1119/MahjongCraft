package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** 牌譜回放場景中人物的不透明度：依顯示方式、視角與鏡頭方向決定，切換時淡入淡出。 */
internal object HistoryReplayFigureOpacity {
    /** 半透明時的不透明度，與原版畫看得見的隱形生物相同。 */
    const val TRANSLUCENT: Float = 0.15f

    /** 從桌子中央看，座位與鏡頭的方向相差小於這個角度時，這一家算擋在鏡頭與桌子中央之間。 */
    const val BLOCKING_DEGREES: Float = 60f

    /** 從完全不透明到完全不畫所需的時間。 */
    val FADE: Duration = 200.milliseconds

    /** 半圈的角度。 */
    private const val HALF_TURN: Float = 180f

    /** 一整圈的角度。 */
    private const val FULL_TURN: Float = 360f

    /**
     * 一家人物應有的不透明度。
     *
     * @param visibility 這一家的顯示方式。
     * @param viewed 是否為目前視角所在的那一家；這一家一律不畫。
     * @param seatYaw 座位面向桌子的朝向。
     * @param cameraYaw 場景鏡頭的水平角度；選某一家視角時等於那一家的座位朝向轉半圈。
     * @return 0 到 1 之間的不透明度，0 表示不畫。
     */
    fun target(visibility: HistoryReplayVisibilityOption, viewed: Boolean, seatYaw: Float, cameraYaw: Float): Float = when {
        viewed -> 0f
        visibility == HistoryReplayVisibilityOption.SHOWN -> 1f
        visibility == HistoryReplayVisibilityOption.TRANSLUCENT -> TRANSLUCENT
        visibility == HistoryReplayVisibilityOption.HIDDEN -> 0f
        blocksView(seatYaw, cameraYaw) -> TRANSLUCENT
        else -> 1f
    }

    /**
     * 判斷一家是否擋在鏡頭與桌子中央之間：從桌子中央看，這一家座位的方向與鏡頭的方向相差小於 [BLOCKING_DEGREES]。
     *
     * @param seatYaw 座位面向桌子的朝向。
     * @param cameraYaw 場景鏡頭的水平角度。
     * @return 擋住時為 true。
     */
    fun blocksView(seatYaw: Float, cameraYaw: Float): Boolean {
        // 選某一家視角時鏡頭角度是座位朝向轉半圈，所以座位朝向轉半圈就是「鏡頭在這一家後方」時的角度。
        val difference = (seatYaw - HALF_TURN - cameraYaw).mod(FULL_TURN)
        return minOf(difference, FULL_TURN - difference) < BLOCKING_DEGREES
    }

    /**
     * 往目標不透明度前進一段時間，[FADE] 走完整個範圍。
     *
     * @param current 目前的不透明度。
     * @param target 目標不透明度。
     * @param elapsed 經過的時間。
     * @return 新的不透明度，不會越過目標。
     */
    fun approach(current: Float, target: Float, elapsed: Duration): Float {
        val step = (elapsed / FADE).toFloat()
        val difference = target - current
        return if (abs(difference) <= step) target else current + step * if (difference > 0f) 1f else -1f
    }
}
