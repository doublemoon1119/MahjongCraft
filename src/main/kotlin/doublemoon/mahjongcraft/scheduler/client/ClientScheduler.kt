package doublemoon.mahjongcraft.scheduler.client

import doublemoon.mahjongcraft.logger
import doublemoon.mahjongcraft.scheduler.ActionBase
import doublemoon.mahjongcraft.scheduler.DelayAction
import doublemoon.mahjongcraft.scheduler.LoopAction
import doublemoon.mahjongcraft.scheduler.RepeatAction
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient

/**
 * 要嚴格防止在任務中呼叫其它任務
 * */
@Environment(EnvType.CLIENT)
object ClientScheduler {

    private val queuedActions = mutableListOf<ActionBase>()
    private val loopActions = mutableListOf<LoopAction>()

    //用來 trace world 改變的事件
    private var doesWorldExist: Boolean = false

    //1 tick = 50 ms
    fun tick(client: MinecraftClient) {
        if ((client.window == null) == doesWorldExist) {
            doesWorldExist = (client.world != null)
            loopActions.forEach { it.resetTimer() }
            queuedActions.filterIsInstance<RepeatAction>().forEach { it.resetTimer() }
        }
        if (client.world == null || client.player == null) {
            queuedActions.clear()
            return
        }
        kotlin.runCatching { queuedActions.removeIf { it.tick() } }
            .onFailure { logger.error("Error when ticking queued actions.", it) }
        loopActions.forEach { it.tick() }
    }

    /**
     * @param delay 單位: ms
     * */
    fun scheduleDelayAction(delay: Long = 0, action: () -> Unit): DelayAction =
        DelayAction(delay, action).also { queuedActions += it }

    /**
     * @param interval 單位: ms
     * */
    fun scheduleRepeatAction(times: Int, interval: Long = 0, action: () -> Unit): RepeatAction =
        RepeatAction(times, interval, action).also { queuedActions += it }

    /**
     * @param interval 單位: ms
     * */
    fun scheduleLoopAction(interval: Long = 0, action: () -> Unit): LoopAction =
        LoopAction(interval, action).also { loopActions += it }

    fun onStopping() {
        queuedActions.clear()
        loopActions.clear()
    }
}