package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/** 單局歷史成功回覆的 session 與查詢上下文鍵。 */
internal sealed interface HistoryRoundCacheKey {
    /** 連線／世界工作階段版本。 */
    val sessionRevision: Long

    /** 對局識別碼。 */
    val matchId: String

    /** 事件頁快取鍵。
     * @property sessionRevision 連線／世界工作階段版本。
     * @property matchId 對局識別碼。
     * @property roundNumber 局序號。
     * @property scope 查詢範圍。
     * @property startTransactionIndex 事件頁起始交易索引。
     * @property limit 事件頁要求上限。
     */
    data class Events(
        override val sessionRevision: Long,
        override val matchId: String,
        val roundNumber: Int,
        val scope: HistoryQueryScopeDto,
        val startTransactionIndex: Int,
        val limit: Int,
    ) : HistoryRoundCacheKey

    /** 桌況快取鍵。
     * @property sessionRevision 連線／世界工作階段版本。
     * @property matchId 對局識別碼。
     * @property roundNumber 局序號。
     * @property scope 查詢範圍。
     * @property position 局內桌況位置。
     */
    data class State(
        override val sessionRevision: Long,
        override val matchId: String,
        val roundNumber: Int,
        val scope: HistoryQueryScopeDto,
        val position: HistoryRoundPositionDto,
    ) : HistoryRoundCacheKey
}

/**
 * 有界的單局歷史成功回覆快取。
 *
 * 預設最多保存 8 個事件頁與 4 個桌況，合計 UTF-8 估算大小不超過 1 MiB，並在 30 秒後過期。
 * 快取不保存錯誤或未通過驗證的回覆；連線／世界切換時由呼叫端清除或切換 session revision。
 *
 * @property now 提供可測試的單調時間。
 * @property ttl 成功項目的存活時間。
 * @property maxEventEntries 事件頁最多保存數量。
 * @property maxStateEntries 桌況最多保存數量。
 * @property maxBytes 所有項目的 UTF-8 估算大小上限。
 */
internal class HistoryRoundCache(
    private val now: () -> Duration = { System.nanoTime().nanoseconds },
    private val ttl: Duration = 30.seconds,
    private val maxEventEntries: Int = 8,
    private val maxStateEntries: Int = 4,
    private val maxBytes: Long = 1L shl 20,
) {
    /**
     * 快取中的成功回覆與其容量及過期時間。
     *
     * @property value 成功回覆 DTO。
     * @property bytes UTF-8 估算大小。
     * @property expiresAt 單調時間上的到期點。
     */
    private data class Entry(
        val value: Any,
        val bytes: Long,
        val expiresAt: Duration,
    )

    /** 依最近存取順序排列的成功資料。 */
    private val entries = LinkedHashMap<HistoryRoundCacheKey, Entry>(16, 0.75f, true)

    /** 目前快取資料的序列化 UTF-8 容量總和。 */
    private var totalBytes = 0L

    /** 取得尚未過期的事件頁。
     * @param key 事件頁快取鍵。
     * @return 成功回覆，或沒有可用項目時為 null。
     */
    fun getEvents(key: HistoryRoundCacheKey.Events): HistoryRoundEventsDto? = get(key) as? HistoryRoundEventsDto

    /** 取得尚未過期的桌況。
     * @param key 桌況快取鍵。
     * @return 成功回覆，或沒有可用項目時為 null。
     */
    fun getState(key: HistoryRoundCacheKey.State): HistoryRoundStateDto? = get(key) as? HistoryRoundStateDto

    /** 保存已驗證的事件頁。
     * @param key 事件頁快取鍵。
     * @param value 已通過驗證的事件頁。
     * @param utf8Bytes 其序列化 UTF-8 大小估算。
     * @return 是否成功加入快取；超過單項容量時為 false。
     */
    fun putEvents(key: HistoryRoundCacheKey.Events, value: HistoryRoundEventsDto, utf8Bytes: Int): Boolean = put(key, value, utf8Bytes.toLong(), maxEventEntries)

    /** 保存已驗證的桌況。
     * @param key 桌況快取鍵。
     * @param value 已通過驗證的桌況。
     * @param utf8Bytes 其序列化 UTF-8 大小估算。
     * @return 是否成功加入快取；超過單項容量時為 false。
     */
    fun putState(key: HistoryRoundCacheKey.State, value: HistoryRoundStateDto, utf8Bytes: Int): Boolean = put(key, value, utf8Bytes.toLong(), maxStateEntries)

    /** 移除其他工作階段的所有結果。
     * @param sessionRevision 目前有效的連線／世界工作階段版本。
     */
    fun invalidateOtherSessions(sessionRevision: Long) {
        removeKeys { it.sessionRevision != sessionRevision }
    }

    /** 移除指定對局的所有事件頁與桌況。
     * @param matchId 欲移除的對局識別碼。
     */
    fun evictMatch(matchId: String) {
        removeKeys { it.matchId == matchId }
    }

    /** 清空所有成功回覆。 */
    fun clear() {
        entries.clear()
        totalBytes = 0L
    }

    /** 讀取並先清除已過期項目。
     * @param key 查詢上下文鍵。
     * @return 成功回覆，或沒有命中時為 null。
     */
    private fun get(key: HistoryRoundCacheKey): Any? {
        purgeExpired()
        return entries[key]?.value
    }

    /** 依類別、總容量及 LRU 順序保存一項成功回覆。
     * @param key 查詢上下文鍵。
     * @param value 成功回覆 DTO。
     * @param bytes UTF-8 估算大小。
     * @param categoryLimit 該類別的項目數上限。
     * @return 是否仍保留在快取中。
     */
    private fun put(key: HistoryRoundCacheKey, value: Any, bytes: Long, categoryLimit: Int): Boolean {
        if (bytes <= 0L || bytes > maxBytes || categoryLimit <= 0) return false
        purgeExpired()
        removeKeys { it == key }
        while (entries.keys.count { it::class == key::class } >= categoryLimit) {
            val oldest = entries.keys.firstOrNull { it::class == key::class } ?: break
            removeKeys { it == oldest }
        }
        entries[key] = Entry(value, bytes, now() + ttl)
        totalBytes += bytes
        while (totalBytes > maxBytes) {
            val oldest = entries.keys.firstOrNull() ?: break
            removeKeys { it == oldest }
        }
        return entries.containsKey(key)
    }

    /** 移除到期項目。 */
    private fun purgeExpired() {
        val current = now()
        val expiredKeys = entries.entries.filter { it.value.expiresAt <= current }.map { it.key }.toSet()
        removeKeys(expiredKeys::contains)
    }

    /** 依條件移除項目並同步容量計數。
     * @param predicate 判斷要移除的鍵。
     */
    private fun removeKeys(predicate: (HistoryRoundCacheKey) -> Boolean) {
        entries.keys.filter(predicate).forEach { key ->
            totalBytes -= entries.remove(key)?.bytes ?: 0L
        }
    }
}
