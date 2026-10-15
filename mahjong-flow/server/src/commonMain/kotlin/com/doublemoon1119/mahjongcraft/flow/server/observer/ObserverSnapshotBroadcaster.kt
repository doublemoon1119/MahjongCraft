package com.doublemoon1119.mahjongcraft.flow.server.observer

import com.doublemoon1119.mahjongcraft.flow.common.observer.model.ObserverSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverAudienceSource
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverSnapshotSender
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.HandReadinessVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.Uuid

/**
 * 依權威狀態把每個房間或對局目前的內容推送給它的觀察者。
 *
 * 推送有兩個觸發來源，兩者都進入同一條 [broadcast] 流程：[observeState] 訂閱權威狀態，狀態一改變就
 * 推送；呼叫端另外定期呼叫 [broadcast]，讓觀察者集合的變化（可見範圍改變、離線、重新連線）也能被
 * 發現。
 *
 * 每位觀察者都記住「上次送給他的內容」，內容相同就不再送出。觀察者離開後其紀錄一併刪除，因此下次
 * 再成為觀察者時會重新收到一份完整內容，不需要另外處理初次送出。
 *
 * @property store 房間與對局的權威狀態。
 * @property visibilityPolicy 產生依觀察者裁切後對局快照的政策。
 * @property handReadinessVisibilityPolicy 產生依觀察者裁切後手牌準備分析快照的政策。
 * @property audienceSource 平台層提供的觀察者來源。
 * @property sender 平台層提供的送出管道。
 */
@OptIn(ExperimentalAtomicApi::class)
class ObserverSnapshotBroadcaster(
    private val store: AuthoritativeStateStore,
    private val visibilityPolicy: GameVisibilityPolicy,
    private val handReadinessVisibilityPolicy: HandReadinessVisibilityPolicy,
    private val audienceSource: ObserverAudienceSource,
    private val sender: ObserverSnapshotSender,
) {
    /**
     * 單一房間或對局的送出紀錄。
     *
     * @property source 產生 [snapshots] 時所依據的權威房間或對局實例；`null` 代表當時兩者都不存在。
     * @property snapshots 各觀察者上次收到的內容。
     */
    private class BroadcastRecord(
        var source: Any?,
        val snapshots: MutableMap<Uuid, ObserverSnapshot?>,
    )

    /**
     * 一次待交付的場地失效請求。
     *
     * @property requestId 本次清理請求的唯一識別碼。
     * @property additionalObserverIds 清理前捕捉的額外觀察者。
     * @property isStillValid 交付前確認來源仍有效的檢查。
     */
    private class PendingInvalidation(
        val requestId: Uuid,
        val additionalObserverIds: Set<Uuid>,
        val isStillValid: () -> Boolean,
    )

    /** 保護送出紀錄，並確保同一時間只有一次推送在進行。 */
    private val mutex = Mutex()

    /** 以房間或對局的 Uuid 索引的送出紀錄。 */
    private val recordsById = mutableMapOf<Uuid, BroadcastRecord>()

    /**
     * 送出紀錄中各場地最後一次看見的觀察者快照。
     *
     * requestVenueInvalidation 不等待 broadcaster mutex，因此不能直接讀取 [recordsById]；這份原子快照讓它在
     * 自己的線性化點捕捉既有接收者，避免稍後的 retainAll 把它們遺失。
     */
    private val recordObserversSnapshot = AtomicReference<Map<Uuid, Set<Uuid>>>(emptyMap())

    /** 不等待 broadcaster mutex 的待交付失效請求。 */
    private val pendingInvalidations = AtomicReference<Map<Uuid, PendingInvalidation>>(emptyMap())

    /** 訂閱權威狀態，於每次狀態變更後推送；這個函式只在呼叫端的協程被取消時結束。 */
    suspend fun observeState() {
        store.state.collect { broadcast() }
    }

    /** 以目前權威狀態推送所有與上次不同的內容。 */
    suspend fun broadcast() = broadcastLatest()

    /**
     * 登記一筆場地失效通知並立即返回；實際交付會與一般快照共用 broadcast 序列。
     *
     * @param id 即將失效的房間或對局識別碼。
     * @param additionalObserverIds 清理前已捕捉、即使位置索引已移除仍應通知的觀察者。
     * @param requestId 本次清理請求的唯一識別碼；舊請求不得清除同 ID 的新請求。
     * @param isStillValid 交付前確認清理來源仍有效的檢查；回傳 false 時只丟棄本次通知。
     */
    fun requestVenueInvalidation(
        id: Uuid,
        additionalObserverIds: Set<Uuid>,
        requestId: Uuid,
        isStillValid: () -> Boolean,
    ) {
        while (true) {
            val current = pendingInvalidations.load()
            val capturedObservers = recordObserversSnapshot.load()[id].orEmpty()
            val previousObservers = current[id]?.additionalObserverIds.orEmpty()
            val next = current + (
                id to PendingInvalidation(
                    requestId = requestId,
                    additionalObserverIds = (capturedObservers + previousObservers + additionalObserverIds).toSet(),
                    isStillValid = isStillValid,
                )
                )
            if (pendingInvalidations.compareAndSet(current, next)) return
        }
    }

    /** 取得最新權威狀態後推送；同一時間只有一次推送在進行，其餘呼叫依序等待。 */
    private suspend fun broadcastLatest() = mutex.withLock {
        val observersById = audienceSource.observersById()
        val pending = pendingInvalidations.load()
        recordsById.keys.retainAll(observersById.keys + pending.keys)
        publishRecordObserversSnapshot()
        val latestState = store.state.value
        val pendingIds = pending.keys
        observersById.forEach { (id, observerIds) ->
            if (id !in pendingIds) broadcastTo(id, observerIds, latestState)
        }
        pending.forEach { (id, request) ->
            val delivered = deliverInvalidation(id, request, observersById[id].orEmpty())
            if (!delivered) {
                observersById[id]?.let { observerIds -> broadcastTo(id, observerIds, latestState) }
            }
        }
    }

    /** 在 broadcaster mutex 內完成單一失效通知，並只移除仍屬於本次請求的 pending 項目。 */
    private suspend fun deliverInvalidation(
        id: Uuid,
        request: PendingInvalidation,
        currentObserverIds: Set<Uuid>,
    ): Boolean {
        try {
            if (!request.isStillValid()) return false
            val recordObserverIds = recordsById[id]?.snapshots?.keys.orEmpty()
            val observerIds = (recordObserverIds + currentObserverIds + request.additionalObserverIds).toSet()
            observerIds.forEach { observerId -> sender.invalidateVenue(id, observerId, request.requestId) }
            recordsById.remove(id)
            publishRecordObserversSnapshot()
            return true
        } finally {
            removePendingIfMatches(id, request.requestId)
        }
    }

    /** 只移除仍是目前請求的 pending 項目，避免舊 callback 清掉同 ID 新請求。 */
    private fun removePendingIfMatches(id: Uuid, requestId: Uuid) {
        while (true) {
            val current = pendingInvalidations.load()
            if (current[id]?.requestId != requestId) return
            val next = current - id
            if (pendingInvalidations.compareAndSet(current, next)) return
        }
    }

    /** 在 broadcaster mutex 內發布目前送出紀錄的不可變接收者快照。 */
    private fun publishRecordObserversSnapshot() {
        recordObserversSnapshot.store(recordsById.mapValues { (_, record) -> record.snapshots.keys.toSet() })
    }

    /** 推送單一房間或對局；內容來源與觀察者集合都沒變時直接跳過，不重新產生任何快照。 */
    private suspend fun broadcastTo(
        id: Uuid,
        observerIds: Set<Uuid>,
        state: AuthoritativeStateSnapshot,
    ) {
        val source: Any? = state.games[id] ?: state.rooms[id]
        val record = recordsById[id]
        if (record != null && record.source === source && record.snapshots.keys == observerIds) return

        val target = record ?: BroadcastRecord(source, mutableMapOf()).also { recordsById[id] = it }
        target.source = source
        target.snapshots.keys.retainAll(observerIds)
        publishRecordObserversSnapshot()
        observerIds.forEach { observerId ->
            val snapshot = snapshotFor(id, observerId, state)
            if (target.snapshots.containsKey(observerId) && target.snapshots[observerId] == snapshot) return@forEach
            // 來源已被取代時不送出也不記錄，否則下一輪內容相同就會被當成已送過而略過。
            val sourceBeforeSend = store.state.value.games[id] ?: store.state.value.rooms[id]
            if (sourceBeforeSend !== source) return@forEach
            target.snapshots[observerId] = snapshot
            publishRecordObserversSnapshot()
            sender.send(id = id, observerId = observerId, snapshot = snapshot)
        }
    }

    /** 產生指定觀察者目前應該看到的內容；房間與對局都不存在時為 `null`。 */
    private fun snapshotFor(id: Uuid, observerId: Uuid, state: AuthoritativeStateSnapshot): ObserverSnapshot? {
        val game = state.games[id]
        if (game != null) {
            return ObserverSnapshot.OfGame(
                game = visibilityPolicy.snapshotFor(game, observerId),
                roundPreparation = visibilityPolicy.roundPreparationSnapshotFor(game, observerId),
                handReadinessAnalysis = handReadinessVisibilityPolicy.snapshotFor(game, observerId),
                aiPlayerIds = game.aiPlayerIds,
            )
        }
        val room = state.rooms[id] ?: return null
        return ObserverSnapshot.OfRoom(room.toSnapshot(observerId))
    }

    /** 清除所有送出紀錄；換 server session 時呼叫，讓下一輪推送重新送出完整內容。 */
    suspend fun clearAll() = mutex.withLock {
        recordsById.clear()
        recordObserversSnapshot.store(emptyMap())
        pendingInvalidations.store(emptyMap())
    }
}
