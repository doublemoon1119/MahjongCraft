package com.doublemoon1119.mahjongcraft.platform.minecraft.animation

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
     *
     * @param C 實體專屬自訂步驟的資料型別。
     * @param queue 目前待執行的步驟。
     * @param steps 要依序附加的步驟。
     * @return 合併相鄰等待步驟後的新佇列。
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
     * 依 [currentGameTime] 推進 [queue]：到期的 [AnimationStep.PlaySound] 不受結構型 step 阻擋，其他瞬間
     * step（[AnimationStep.Teleport]／[AnimationStep.SetInvisible]／[AnimationStep.Custom]）連續處理到下一個
     * 計時 step 為止；計時 step（[AnimationStep.WaitUntil]／[AnimationStep.PlayMotion]）未到期就停在原地，
     * 等下一次呼叫再檢查。[AnimationStep.WaitUntil]
     * 本身就攜帶絕對到期時間，不需要 [activeStepEndGameTime] 追蹤；[activeStepEndGameTime] 只用來記錄
     * [AnimationStep.PlayMotion] 是不是剛開始（讓呼叫端只在啟動當下同步一次 render 用欄位），存的是
     * 絕對 game time，跨存讀檔正確恢復。
     *
     * @param C 實體專屬自訂步驟的資料型別。
     * @param queue 目前待處理的步驟。
     * @param activeStepEndGameTime 已開始播放之動作的絕對結束時間；沒有進行中的動作時為 null。
     * @param currentGameTime 目前的絕對 game time。
     * @return 套用步驟及剩餘佇列的快照。
     */
    fun <C> tick(
        queue: List<AnimationStep<C>>,
        activeStepEndGameTime: Long?,
        currentGameTime: Long,
    ): AnimationQueueTickResult<C> {
        val dueSounds = queue.filterIsInstance<AnimationStep.PlaySound>()
            .filter { sound -> currentGameTime >= sound.playAtGameTime }
        val futureSounds = queue.filterIsInstance<AnimationStep.PlaySound>()
            .filter { sound -> currentGameTime < sound.playAtGameTime }
        val remaining = ArrayDeque(queue.filterNot { step -> step is AnimationStep.PlaySound })
        var endGameTime = activeStepEndGameTime
        val appliedInstantSteps = mutableListOf<AnimationStep<C>>()
        appliedInstantSteps += dueSounds
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

                is AnimationStep.PlaySound -> error("Scheduled sound cues must be separated before structural queue processing")

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
            remainingQueue = futureSounds + remaining,
            activeStepEndGameTime = endGameTime,
        )
    }
}
