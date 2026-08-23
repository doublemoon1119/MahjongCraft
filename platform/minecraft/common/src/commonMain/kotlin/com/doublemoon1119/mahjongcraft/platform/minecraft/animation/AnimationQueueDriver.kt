package com.doublemoon1119.mahjongcraft.platform.minecraft.animation

/**
 * 一次 [AnimationQueueDriver.tick] 呼叫的結果：依序套用的瞬間動作（[appliedInstantSteps]，只含
 * [AnimationStep.Teleport]／[AnimationStep.SetInvisible]／[AnimationStep.Custom]，不含
 * [AnimationStep.WaitUntil]——純等待沒有任何可套用的動作），以及套用後的佇列／計時進度快照。
 *
 * [startedPlayMotion] 非 `null` 代表這個 tick 有一段 [AnimationStep.PlayMotion] 剛開始播放，呼叫端
 * 需要據此同步 render 用欄位；[completedPlayMotion] 為 `true` 代表這個 tick 有一段
 * [AnimationStep.PlayMotion] 播完了（`durationTicks` 為 `0` 時同一個 tick 內可能兩者都成立）。
 */
data class AnimationQueueTickResult<C>(
    val appliedInstantSteps: List<AnimationStep<C>>,
    val startedPlayMotion: AnimationStep.PlayMotion?,
    val completedPlayMotion: Boolean,
    val remainingQueue: List<AnimationStep<C>>,
    val activeStepEndGameTime: Long?,
)

/**
 * 動畫佇列的純邏輯處理核心，不依賴任何 Minecraft API——真正的座標／姿態／render 欄位變更（副作用）
 * 由呼叫端（`AnimatedMahjongEntity`）依 [AnimationQueueTickResult] 執行，這裡只負責「依目前佇列與
 * game time，這個 tick 該往前推進到什麼狀態」這個純粹的判斷，讓佇列的到期／推進邏輯可以脫離
 * `Entity`／`World` 獨立測試。
 */
object AnimationQueueDriver {
    /**
     * 把 [steps] 附加至 [queue]，並把相鄰的 [AnimationStep.WaitUntil] 合併成較晚的絕對時間；等待之間若有
     * 任何實際動作就維持原序，避免改變不同動畫階段的時間語意。
     */
    fun <C> append(
        queue: List<AnimationStep<C>>,
        steps: List<AnimationStep<C>>,
    ): List<AnimationStep<C>> = buildList {
        addAll(queue)
        steps.forEach { step ->
            val previousWait = lastOrNull() as? AnimationStep.WaitUntil
            if (step is AnimationStep.WaitUntil && previousWait != null) {
                if (step.gameTime > previousWait.gameTime) {
                    removeAt(lastIndex)
                    add(step)
                }
            } else {
                add(step)
            }
        }
    }

    /**
     * 依 [currentGameTime] 推進 [queue]：瞬間 step（[AnimationStep.Teleport]／[AnimationStep.SetInvisible]／
     * [AnimationStep.Custom]）連續處理到下一個計時 step 為止；計時 step（[AnimationStep.WaitUntil]／
     * [AnimationStep.PlayMotion]）未到期就停在原地，等下一次呼叫再檢查。[AnimationStep.WaitUntil]
     * 本身就攜帶絕對到期時間，不需要 [activeStepEndGameTime] 追蹤；[activeStepEndGameTime] 只用來記錄
     * [AnimationStep.PlayMotion] 是不是剛開始（讓呼叫端只在啟動當下同步一次 render 用欄位），存的是
     * 絕對 game time，跨存讀檔正確恢復。
     */
    fun <C> tick(
        queue: List<AnimationStep<C>>,
        activeStepEndGameTime: Long?,
        currentGameTime: Long,
    ): AnimationQueueTickResult<C> {
        val remaining = ArrayDeque(queue)
        var endGameTime = activeStepEndGameTime
        val appliedInstantSteps = mutableListOf<AnimationStep<C>>()
        var startedPlayMotion: AnimationStep.PlayMotion? = null
        var completedPlayMotion = false

        while (true) {
            val step = remaining.firstOrNull() ?: break
            when (step) {
                is AnimationStep.Teleport, is AnimationStep.SetInvisible, is AnimationStep.Custom -> {
                    appliedInstantSteps += step
                    remaining.removeFirst()
                }

                is AnimationStep.WaitUntil -> {
                    if (currentGameTime < step.gameTime) break
                    remaining.removeFirst()
                }

                is AnimationStep.PlayMotion -> {
                    val justStarted = endGameTime == null
                    val resolvedEndGameTime = endGameTime ?: (currentGameTime + step.durationTicks)
                    endGameTime = resolvedEndGameTime
                    if (justStarted) startedPlayMotion = step
                    if (currentGameTime < resolvedEndGameTime) break
                    endGameTime = null
                    remaining.removeFirst()
                    completedPlayMotion = true
                }
            }
        }

        return AnimationQueueTickResult(
            appliedInstantSteps = appliedInstantSteps,
            startedPlayMotion = startedPlayMotion,
            completedPlayMotion = completedPlayMotion,
            remainingQueue = remaining,
            activeStepEndGameTime = endGameTime,
        )
    }
}
