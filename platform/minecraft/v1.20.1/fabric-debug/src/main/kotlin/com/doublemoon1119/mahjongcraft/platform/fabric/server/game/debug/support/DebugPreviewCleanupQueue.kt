package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 依絕對 game time 到期的臨時 entity 清除佇列。
 *
 * 只保存「到期時刻」與兩個由呼叫端提供的回呼，本身不認識任何 Minecraft 型別，因此可以單獨驗證到期
 * 判定與佇列移除語意。世界時間與實際清除動作由 [DebugPreviewEntityLifecycle] 綁定。
 */
internal class DebugPreviewCleanupQueue {
    /** 尚未到期的清除排程；跨執行緒的 server tick 與指令執行緒共用，因此使用併發佇列。 */
    private val tasks = ConcurrentLinkedQueue<Task>()

    /** 目前尚未到期的排程數量。 */
    val pendingCount: Int
        get() = tasks.size

    /**
     * 排定一筆清除工作。
     *
     * @param endGameTime 到期的絕對 game time。
     * @param currentGameTime 取得該工作所屬世界目前 game time 的回呼。
     * @param discard 到期時實際清除目標的回呼。
     */
    fun schedule(
        endGameTime: Long,
        currentGameTime: () -> Long,
        discard: () -> Unit,
    ) {
        tasks += Task(
            endGameTime = endGameTime,
            currentGameTime = currentGameTime,
            discard = discard,
        )
    }

    /** 清除所有已到期的工作並將它們移出佇列；未到期的工作保持原狀等待下一次檢查。 */
    fun discardExpired() {
        val iterator = tasks.iterator()
        while (iterator.hasNext()) {
            val task = iterator.next()
            if (task.currentGameTime() >= task.endGameTime) {
                task.discard()
                iterator.remove()
            }
        }
    }

    /** 單筆清除排程。 */
    private class Task(
        /** 到期的絕對 game time。 */
        val endGameTime: Long,
        /** 取得目前 game time 的回呼。 */
        val currentGameTime: () -> Long,
        /** 到期時執行的清除動作。 */
        val discard: () -> Unit,
    )
}
