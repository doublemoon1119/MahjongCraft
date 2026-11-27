package com.doublemoon1119.mahjongcraft.platform.minecraft.animation

/**
 * 一次 [AnimationQueueDriver.tick] 中 [AnimationStep.PlayMotion] 開始或結束的事件，依發生順序排列。
 *
 * - 上一段動作結束、下一段在同一個 tick 開始時，先有 [Completed] 再有 [Started]。
 * - 長度為 `0` 的動作在同一個 tick 內先 [Started] 再 [Completed]。
 */
sealed interface AnimationMotionEvent {
    /**
     * 一段動作開始播放，呼叫端據此同步 render 用欄位。
     *
     * @property step 開始播放的動作。
     */
    data class Started(val step: AnimationStep.PlayMotion) : AnimationMotionEvent

    /** 一段動作播完。 */
    data object Completed : AnimationMotionEvent
}
