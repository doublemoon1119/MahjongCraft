package doublemoon.mahjongcraft.scheduler

import java.time.Instant

/**
 * 有限地重複執行動作
 * @param times 要執行幾次
 * @param interval 要執行的間隔豪秒數,每次動作最少都會相隔 1 個 tick,所以間隔小於 1 個標準 tick 的時間 (1/20 秒 (50 ms)) 的動作會延到下個 tick 執行
 * @param action 每個間隔時間後執行的動作
 * */
class RepeatAction(
    val times: Int,
    private val interval: Long,
    override val action: () -> Unit
) : ActionBase {
    override var stop: Boolean = false
    override var timeToAction: Long = Instant.now().toEpochMilli() + interval
    var count: Int = 0

    override fun tick(): Boolean {
        if (stop) return true
        if (Instant.now().toEpochMilli() >= timeToAction) {
            action.invoke()
            count++
            if (count < times) resetTimer()
            else return true
        }
        return false
    }

    fun resetTimer() {
        timeToAction = Instant.now().toEpochMilli() + interval
    }
}