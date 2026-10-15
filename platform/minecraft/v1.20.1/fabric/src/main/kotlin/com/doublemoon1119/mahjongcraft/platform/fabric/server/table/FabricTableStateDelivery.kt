package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.Uuid

/**
 * 將可重建桌況的回覆與桌子移除通知序列化到伺服器主執行緒。
 *
 * 準備內容可能掛起，因此送出前再次檢查 session 與位置條目身分；最後檢查與送出不可掛起。
 * 移除請求只登記記憶體標記，不等待交付鎖，避免主執行緒清理入口互相等待。
 *
 * @property dispatchers 伺服器主執行緒排程器。
 * @property environment 目前有效的桌級來源。
 */
@OptIn(ExperimentalAtomicApi::class)
class FabricTableStateDelivery(
    private val dispatchers: CoroutineDispatchers,
    private val environment: TableStateDeliveryEnvironment,
) {
    /** 保護最後來源檢查及所有桌況封包交付，不與權威交易鎖共用。 */
    private val mutex = Mutex()

    /** 已開始移除的原來源；新條目即使 revision 相同也不會被舊標記阻擋。 */
    private val removals = AtomicReference<Map<Uuid, FabricTableStateTicket>>(emptyMap())

    /**
     * 取得工作建立時的來源，不存取原生世界物件。
     *
     * @param tableId 工作對應的桌子。
     * @return 有效來源票據；桌子或 session 不存在時為 null。
     */
    fun capture(tableId: Uuid): FabricTableStateTicket? {
        val sessionId = environment.sessionId ?: return null
        val entry = environment.locationEntry(tableId) ?: return null
        val ticket = FabricTableStateTicket(tableId, sessionId, entry)
        return ticket.takeIf(::isCurrent)
    }

    /**
     * 檢查票據仍對應原來源，不只比較 revision 數字。
     *
     * @param ticket 工作建立時的來源。
     * @return 原 session 與位置條目仍有效且未開始移除時為 true。
     */
    fun isCurrent(ticket: FabricTableStateTicket): Boolean = matchesSource(ticket) && removals.load()[ticket.tableId]?.let { it.sessionId == ticket.sessionId && it.entry === ticket.entry } != true

    /**
     * 檢查物理來源仍相同，允許本次正在移除的來源完成後續清理。
     *
     * @param ticket 清理前捕捉的來源。
     * @return session 與原位置條目仍相同時為 true。
     */
    fun hasSameSource(ticket: FabricTableStateTicket): Boolean = matchesSource(ticket)

    /**
     * 登記成功清理的來源，後續工作不再送出它的桌況。
     *
     * @param ticket 清理前捕捉的來源。
     * @return 原來源仍相符並已登記時為 true。
     */
    fun beginRemoval(ticket: FabricTableStateTicket): Boolean {
        if (!matchesSource(ticket)) return false
        updateRemovals { it + (ticket.tableId to ticket) }
        return true
    }

    /**
     * 檢查原來源已消失，且沒有同 ID 的新來源需要保護。
     *
     * @param ticket 本次清理的來源。
     * @return 仍是原 session、位置與權威來源均不存在時為 true。
     */
    fun isRemoved(ticket: FabricTableStateTicket): Boolean = environment.sessionId == ticket.sessionId &&
        environment.locationEntry(ticket.tableId) == null &&
        !environment.hasAuthoritativeState(ticket.tableId)

    /**
     * 在主執行緒序列準備並送出回覆；來源在等待或準備期間消失時略過。
     *
     * @param ticket 呼叫端在工作建立時捕捉的來源。
     * @param prepare 可掛起的內容準備；沒有可交付內容時回傳 null。
     * @param send 最後來源檢查後立即執行的送出動作，不得掛起。
     * @return 內容已交付時為 true。
     */
    suspend fun <T : Any> prepareAndSend(
        ticket: FabricTableStateTicket,
        prepare: suspend () -> T?,
        send: (T) -> Unit,
    ): Boolean = withContext(dispatchers.main) {
        mutex.withLock {
            if (!isCurrent(ticket)) return@withLock false
            val value = prepare() ?: return@withLock false
            if (!isCurrent(ticket)) return@withLock false
            send(value)
            true
        }
    }

    /**
     * 在相同交付序列送出真正的桌子移除，不在新來源建立後套用舊通知。
     *
     * @param ticket 清理前捕捉的來源。
     * @param send 不可掛起的移除通知送出動作。
     * @return 原來源已移除且通知已交付時為 true。
     */
    suspend fun sendRemoval(ticket: FabricTableStateTicket, send: () -> Unit): Boolean = withContext(dispatchers.main) {
        mutex.withLock {
            if (!isRemoved(ticket)) return@withLock false
            send()
            true
        }
    }

    /**
     * 只釋放本次請求的標記，不清掉同桌後來的移除請求。
     *
     * @param ticket 要釋放的來源票據。
     */
    fun finishRemoval(ticket: FabricTableStateTicket) {
        updateRemovals { current ->
            if (current[ticket.tableId] === ticket) current - ticket.tableId else current
        }
    }

    /**
     * 找到仍屬於指定失效請求的票據，不讓舊失效交付使用新來源的票據。
     *
     * @param tableId 失效來源的桌子。
     * @param requestId 本次失效請求的唯一識別碼。
     * @return 符合請求身分的票據；已釋放或已取代時為 null。
     */
    fun removalTicket(tableId: Uuid, requestId: Uuid): FabricTableStateTicket? = removals.load()[tableId]?.takeIf { it.requestId == requestId }

    /**
     * 檢查 session 與位置物件身分，不讀取原生世界。
     *
     * @param ticket 要檢查的來源。
     * @return 原來源仍存在時為 true。
     */
    private fun matchesSource(ticket: FabricTableStateTicket): Boolean = environment.sessionId == ticket.sessionId && environment.locationEntry(ticket.tableId) === ticket.entry

    /**
     * 原子替換不可變移除標記快照，供清理及完成回呼在任意執行緒使用。
     *
     * @param update 由目前快照產生新快照的無副作用轉換。
     */
    private fun updateRemovals(update: (Map<Uuid, FabricTableStateTicket>) -> Map<Uuid, FabricTableStateTicket>) {
        while (true) {
            val current = removals.load()
            val next = update(current)
            if (removals.compareAndSet(current, next)) return
        }
    }
}
