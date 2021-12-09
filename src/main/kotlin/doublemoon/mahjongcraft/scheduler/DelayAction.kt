package doublemoon.mahjongcraft.scheduler

import doublemoon.mahjongcraft.logger
import java.time.Instant

/**
 * @param delay 要延遲的豪秒數, 單位: ms
 * @param action 延遲後執行的動作
 * */
class DelayAction(
    delay: Long,
    override val action: () -> Unit
) : ActionBase {
    override var stop: Boolean = false
    override var timeToAction: Long = Instant.now().toEpochMilli() + delay

    override fun tick(): Boolean {
        return when {
            stop -> true
            Instant.now().toEpochMilli() >= timeToAction -> {
                kotlin.runCatching { action.invoke() }.onFailure { logger.error("Error when invoking DelayAction", it) }
                true
            }
            else -> false
        }
    }

}