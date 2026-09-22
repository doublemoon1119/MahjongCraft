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
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
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
 * @property audienceSource 平台層提供的觀察者來源。
 * @property sender 平台層提供的送出管道。
 */
@Single
class ObserverSnapshotBroadcaster(
    private val store: AuthoritativeStateStore,
    private val visibilityPolicy: GameVisibilityPolicy,
    private val handReadinessVisibilityPolicy: HandReadinessVisibilityPolicy,
    @Provided private val audienceSource: ObserverAudienceSource,
    @Provided private val sender: ObserverSnapshotSender,
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

    /** 保護送出紀錄，並確保同一時間只有一次推送在進行。 */
    private val mutex = Mutex()

    /** 以房間或對局的 Uuid 索引的送出紀錄。 */
    private val recordsById = mutableMapOf<Uuid, BroadcastRecord>()

    /** 訂閱權威狀態，於每次狀態變更後推送；這個函式只在呼叫端的協程被取消時結束。 */
    suspend fun observeState() {
        store.state.collect { state -> broadcast(state) }
    }

    /** 以目前權威狀態推送所有與上次不同的內容。 */
    suspend fun broadcast() = broadcast(store.state.value)

    /** 以 [state] 為準推送；同一時間只有一次推送在進行，其餘呼叫依序等待。 */
    private suspend fun broadcast(state: AuthoritativeStateSnapshot) = mutex.withLock {
        val observersById = audienceSource.observersById()
        recordsById.keys.retainAll(observersById.keys)
        observersById.forEach { (id, observerIds) -> broadcastTo(id, observerIds, state) }
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
        observerIds.forEach { observerId ->
            val snapshot = snapshotFor(id, observerId, state)
            if (target.snapshots.containsKey(observerId) && target.snapshots[observerId] == snapshot) return@forEach
            target.snapshots[observerId] = snapshot
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
            )
        }
        val room = state.rooms[id] ?: return null
        return ObserverSnapshot.OfRoom(room.toSnapshot(observerId))
    }

    /** 清除所有送出紀錄；換 server session 時呼叫，讓下一輪推送重新送出完整內容。 */
    suspend fun clearAll() = mutex.withLock { recordsById.clear() }
}
