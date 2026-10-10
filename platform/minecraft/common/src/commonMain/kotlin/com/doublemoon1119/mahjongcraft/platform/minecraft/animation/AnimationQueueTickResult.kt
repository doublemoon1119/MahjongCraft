package com.doublemoon1119.mahjongcraft.platform.minecraft.animation

/**
 * 一次 [AnimationQueueDriver.tick] 呼叫的結果：依序套用的瞬間動作（[appliedInstantSteps]，只含
 * [AnimationStep.Teleport]／[AnimationStep.SetInvisible]／[AnimationStep.PlaySound]／[AnimationStep.Custom]，不含
 * [AnimationStep.WaitUntil]——純等待沒有任何可套用的動作），以及套用後的佇列／計時進度快照。
 *
 * [startedPlayMotion] 非 `null` 代表這個 tick 有一段 [AnimationStep.PlayMotion] 剛開始播放，呼叫端
 * 需要據此同步 render 用欄位；[completedPlayMotion] 為 `true` 代表這個 tick 有一段
 * [AnimationStep.PlayMotion] 播完了（`durationTicks` 為 `0` 時同一個 tick 內可能兩者都成立）。
 *
 * @property appliedInstantSteps 這次 tick 實際套用的瞬間動畫步驟。
 * @property startedPlayMotion 這次 tick 剛開始播放的動作；沒有開始播放時為 `null`。
 * @property completedPlayMotion 這次 tick 是否完成一段動作。
 * @property remainingQueue 套用後仍待處理的動畫佇列。
 * @property activeStepEndGameTime 目前播放中的計時步驟結束時間；沒有進行中的步驟時為 `null`。
 */
data class AnimationQueueTickResult<C>(
    val appliedInstantSteps: List<AnimationStep<C>>,
    val startedPlayMotion: AnimationStep.PlayMotion?,
    val completedPlayMotion: Boolean,
    val remainingQueue: List<AnimationStep<C>>,
    val activeStepEndGameTime: Long?,
)
