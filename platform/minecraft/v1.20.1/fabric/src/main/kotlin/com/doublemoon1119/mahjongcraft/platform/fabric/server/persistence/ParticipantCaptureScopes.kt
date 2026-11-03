package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/** 依房間生命週期序列化暫態角色擷取，不讓一桌的發布等待阻塞其他桌。 */
internal class ParticipantCaptureScopes {
    /** 只保護登記，不在持鎖時執行操作或等待協程。 */
    private val monitor = Any()

    /** 使用中及等待中的房間；最後一位離開時移除。 */
    private val entries = mutableMapOf<Uuid, Entry>()

    /**
     * 等待同一房間的前次擷取結束，再執行本次操作。
     *
     * @param roomInstanceId 本次房間生命週期。
     * @param action 擷取、權威提交與釋放。
     * @return 操作的結果。
     */
    suspend fun <T> withScope(roomInstanceId: Uuid, action: suspend () -> T): T {
        val entry = retain(roomInstanceId)
        return try {
            entry.mutex.withLock { action() }
        } finally {
            release(roomInstanceId, entry)
        }
    }

    /**
     * 不等待地執行刷新；已有開局或加入操作時留待下輪，不在權威鎖內等待另一把鎖。
     *
     * @param roomInstanceId 本次房間生命週期。
     * @param action 不會掛起的刷新操作。
     * @return 操作結果；房間正忙碌時為 null。
     */
    fun <T : Any> tryWithScope(roomInstanceId: Uuid, action: () -> T): T? {
        val entry = retain(roomInstanceId)
        try {
            if (!entry.mutex.tryLock()) return null
            return try {
                action()
            } finally {
                entry.mutex.unlock()
            }
        } finally {
            release(roomInstanceId, entry)
        }
    }

    /**
     * 登記使用者，包括尚在等待的工作。
     *
     * @param id 本次房間生命週期。
     * @return 同一房間共用的執行權。
     */
    private fun retain(id: Uuid): Entry = synchronized(monitor) {
        entries.getOrPut(id) { Entry() }.also { it.users++ }
    }

    /**
     * 最後一位使用者離開時清除登記，不累積已解散房間。
     *
     * @param id 本次房間生命週期。
     * @param entry 本次取得的執行權。
     */
    private fun release(id: Uuid, entry: Entry) = synchronized(monitor) {
        entry.users--
        if (entry.users == 0 && entries[id] === entry) entries.remove(id)
        Unit
    }

    /**
     * 同一房間的執行權與使用者數量。
     *
     * @property mutex 保護本次擷取的協程鎖。
     * @property users 已登記的執行中及等待中工作，只在 monitor 內變更。
     */
    private class Entry(val mutex: Mutex = Mutex(), var users: Int = 0)
}
