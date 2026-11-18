package doublemoon.mahjongcraft.scheduler.client

import doublemoon.mahjongcraft.scheduler.ActionBase
import doublemoon.mahjongcraft.scheduler.DelayAction
import doublemoon.mahjongcraft.scheduler.LoopAction
import doublemoon.mahjongcraft.scheduler.RepeatAction
import java.util.concurrent.CopyOnWriteArrayList
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient

/**
 * 要嚴格防止在任務中呼叫其它任務
 * */
@Environment(EnvType.CLIENT)
object ClientScheduler {

    // Thread-safe: actions are scheduled from coroutines on other threads while tick() iterates.
    private val queuedActions = CopyOnWriteArrayList<ActionBase>()
    private val loopActions = CopyOnWriteArrayList<LoopAction>()

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

        val copyOfQueuedAction = queuedActions.toList()
        val copyOfLoopAction = loopActions.toList()
        copyOfQueuedAction.forEach { if (it.tick()) queuedActions.remove(it) }
        copyOfLoopAction.forEach { it.tick() }
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