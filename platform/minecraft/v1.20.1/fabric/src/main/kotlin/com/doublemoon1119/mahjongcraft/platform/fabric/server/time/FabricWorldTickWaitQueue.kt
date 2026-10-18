package com.doublemoon1119.mahjongcraft.platform.fabric.server.time

import kotlinx.coroutines.DisposableHandle

/**
 * 依鍵值與絕對 tick 管理等待項目；每個項目只會被完成、取消或移除一次。
 *
 * @param K 各自擁有時鐘與生命週期的對象型別。
 */
internal class FabricWorldTickWaitQueue<K> {
    /** 保護等待項目的登記與一次性取出。 */
    private val lock = Any()

    /** 尚未交付或取消的等待項目。 */
    private val entries = mutableListOf<Entry<K>>()

    /**
     * 登記等待項目，回呼由取出項目的呼叫端在鎖外執行。
     *
     * @param key 擁有時鐘的對象。
     * @param targetTick 要到達的絕對 tick。
     * @param onReady 時鐘到期後的完成動作。
     * @param onCancelled 對象停止時的取消動作。
     * @return 可移除此筆等待且不執行回呼的控制柄。
     */
    fun register(
        key: K,
        targetTick: Long,
        onReady: () -> Unit,
        onCancelled: () -> Unit,
    ): DisposableHandle {
        val entry = Entry(key, targetTick, onReady, onCancelled)
        synchronized(lock) { entries += entry }
        return DisposableHandle { synchronized(lock) { entries.remove(entry) } }
    }

    /**
     * 取出指定對象已到期的項目，不在鎖內執行回呼。
     *
     * @param key 擁有時鐘的對象。
     * @param currentTick 目前的絕對 tick。
     * @return 本次取得的完成動作；同一筆等待不會再被其他呼叫取出。
     */
    fun pollReady(key: K, currentTick: Long): List<() -> Unit> = synchronized(lock) {
        entries.toList()
            .filter { it.key == key && it.targetTick <= currentTick }
            .filter { entries.remove(it) }
            .map { it.onReady }
    }

    /**
     * 取出指定對象的全部等待項目，交由呼叫端取消。
     *
     * @param key 已停止的對象。
     * @return 本次取得的取消動作。
     */
    fun cancel(key: K): List<() -> Unit> = synchronized(lock) {
        entries.toList()
            .filter { it.key == key }
            .filter { entries.remove(it) }
            .map { it.onCancelled }
    }

    /**
     * 一筆獨立等待；即使欄位完全相同，也不能取代另一筆等待的身分。
     *
     * @property key 擁有時鐘的對象。
     * @property targetTick 目標絕對 tick。
     * @property onReady 到期完成動作。
     * @property onCancelled 停止取消動作。
     */
    private class Entry<K>(
        val key: K,
        val targetTick: Long,
        val onReady: () -> Unit,
        val onCancelled: () -> Unit,
    )
}
