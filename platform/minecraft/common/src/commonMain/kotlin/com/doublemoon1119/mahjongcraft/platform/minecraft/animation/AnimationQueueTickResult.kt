package com.doublemoon1119.mahjongcraft.platform.minecraft.animation

/**
 * 一次 [AnimationQueueDriver.tick] 呼叫的結果：依序套用的瞬間動作（[appliedInstantSteps]，只含
 * [AnimationStep.Teleport]／[AnimationStep.SetInvisible]／[AnimationStep.PlaySound]／[AnimationStep.Custom]，不含
 * [AnimationStep.WaitUntil]——純等待沒有任何可套用的動作），以及套用後的佇列／計時進度快照。
 *
 * - 呼叫端先套用 [appliedInstantSteps]，再依序處理 [motionEvents]。
 * - [motionEvents] 依發生順序排列，呼叫端必須照順序處理，最後的狀態才會對應仍在播放的動作，見 [AnimationMotionEvent]。
 *
 * @param C 實體專屬自訂步驟的資料型別。
 * @property appliedInstantSteps 這次 tick 實際套用的瞬間動畫步驟。
 * @property motionEvents 這次 tick 中動作開始與結束的事件，依發生順序排列。
 * @property remainingQueue 套用後仍待處理的動畫佇列。
 * @property activeStepEndGameTime 目前播放中的計時步驟結束時間。沒有進行中的步驟時為 `null`。
 */
data class AnimationQueueTickResult<C>(
    val appliedInstantSteps: List<AnimationStep<C>>,
    val motionEvents: List<AnimationMotionEvent>,
    val remainingQueue: List<AnimationStep<C>>,
    val activeStepEndGameTime: Long?,
)
