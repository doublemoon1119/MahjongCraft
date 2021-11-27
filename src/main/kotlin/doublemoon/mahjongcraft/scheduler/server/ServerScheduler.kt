package doublemoon.mahjongcraft.scheduler.server

import doublemoon.mahjongcraft.game.GameManager
import doublemoon.mahjongcraft.logger
import doublemoon.mahjongcraft.scheduler.ActionBase
import doublemoon.mahjongcraft.scheduler.DelayAction
import doublemoon.mahjongcraft.scheduler.LoopAction
import doublemoon.mahjongcraft.scheduler.RepeatAction
import net.minecraft.server.MinecraftServer

/**
 * 要嚴格防止在任務中呼叫其它任務
 * */
object ServerScheduler {

    private val queuedActions = mutableListOf<ActionBase>()
    private val loopActions = mutableListOf<LoopAction>()

    fun tick(server: MinecraftServer) {
        if (!server.isRunning) return
        kotlin.runCatching { queuedActions.removeIf { it.tick() } }.onFailure { it.printStackTrace() }
//        queuedActions.removeIf { it.tick() }
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

    fun removeQueuedAction(action: ActionBase): Boolean = queuedActions.remove(action)

    fun onStopping(server: MinecraftServer) {
        queuedActions.clear()
        loopActions.clear()
        GameManager.games.forEach { it.onServerStopping(server) }
        logger.info("${GameManager.games.size} games cleared")
    }
}