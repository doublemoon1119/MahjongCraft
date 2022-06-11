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

        /*
        * 因為有可能對 queuedActions 使用 removeIf 或者 forEach 時, 其他地方會在 queuedActions 迭代的過程中,
        * 對 queuedActions "添加"新的物件, 導致產生了 java.util.ConcurrentModificationException,
        * 為了避免錯誤, 雖然可能有點浪費資源, 但這邊簡單粗暴直接 copy 一個新的 list 來用
        * */
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

    fun removeQueuedAction(action: ActionBase): Boolean = queuedActions.remove(action)

    fun onStopping(server: MinecraftServer) {
        queuedActions.clear()
        loopActions.clear()
        GameManager.games.forEach { it.onServerStopping(server) }
        logger.info("${GameManager.games.size} games cleared")
    }
}