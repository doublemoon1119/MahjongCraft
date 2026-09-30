package com.doublemoon1119.mahjongcraft.flow.server.state

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryPruningConfirmation
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingTerminal
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableChange
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableResult
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * 伺服器目前持有的完整 Room 與 Game 狀態快照。
 *
 * @property rooms 以桌子 UUID 索引的等待階段狀態。
 * @property games 以桌子 UUID 索引的進行中狀態。
 * @property historyRecordingState 與 Game 生命週期分離的待寫歷史事件。
 */
data class AuthoritativeStateSnapshot(
    val rooms: Map<Uuid, Room> = emptyMap(),
    val games: Map<Uuid, Game> = emptyMap(),
    val historyRecordingState: HistoryRecordingState = HistoryRecordingState(),
) {
    init {
        require(rooms.all { (id, room) -> id == room.id }) { "Room index must match its state ID" }
        require(games.all { (id, game) -> id == game.id }) { "Game index must match its state ID" }
        require(rooms.keys.intersect(games.keys).isEmpty()) {
            "The same table ID must not exist as both a room and a game"
        }
    }
}

/**
 * 單次伺服器權威狀態交易的結果。
 *
 * @property state 交易完成後的完整狀態。
 * @property result 回傳給呼叫端的結果。
 * @property historyDraftsByTableId 與本次狀態變更一起提交的權威歷史事實。
 * @property historyRecordingFailures 歷史事件記錄失敗的桌子 ID；提交狀態時為對應場次記錄序號缺口。
 */
data class AuthoritativeStateUpdate<T>(
    val state: AuthoritativeStateSnapshot,
    val result: T,
    val historyDraftsByTableId: Map<Uuid, List<HistoryEventDraft>> = emptyMap(),
    val historyRecordingFailures: Set<Uuid> = emptySet(),
)

/**
 * 以同一把互斥鎖管理 Room 與 Game 的共用狀態儲存。
 *
 * 所有變更皆透過 [update] 提交，使 Room → Game 等跨集合操作能在單次交易內完成。變更後的狀態同時
 * 發布到 [state]，供需要在狀態改變時反應的服務訂閱。
 *
 * @param historyRecordingEnabled 建立時的新場記錄預設；正式政策由伺服器組合入口套用，與儲存連線無關。
 * @property historyClock 產生歷史事件 UTC 時間戳的時鐘；事件順序仍由序號決定。
 * @property maxPendingHistoryEvents 待寫佇列的容量上限；超出時只保留序號缺口，不阻塞對局。
 */
@Single
class AuthoritativeStateStore(
    historyRecordingEnabled: Boolean = false,
    private val historyClock: Clock = Clock.System,
    private val maxPendingHistoryEvents: Int = 256,
) {
    init {
        require(maxPendingHistoryEvents >= 0) { "Pending history capacity must not be negative" }
    }

    /** 目前新場資格政策；變更及權威交易皆由同一 mutex 排序。 */
    @Volatile private var recordingPolicy = HistoryRecordingPolicy(enabled = historyRecordingEnabled)

    /** 歷史儲存端目前是否可接受新的事件。 */
    @Volatile var isHistoryStorageAvailable: Boolean = true
        private set

    /** 目前是否記錄歷史事件。 */
    val isHistoryRecordingEnabled: Boolean get() = recordingPolicy.enabled

    /** 保護狀態、dirty flag 與 dirty listener 的互斥鎖。 */
    private val mutex = Mutex()

    /** 目前的不可變伺服器權威狀態。 */
    private val mutableState = MutableStateFlow(AuthoritativeStateSnapshot())

    /**
     * 目前狀態，並在每次實際變更後發出新值。
     *
     * 訂閱者取得的是已提交的狀態；發出時機在 [update] 的交易內，因此不會觀察到中間狀態。
     */
    val state: StateFlow<AuthoritativeStateSnapshot> = mutableState.asStateFlow()

    /** 目前狀態的內部存取捷徑。 */
    private var currentState: AuthoritativeStateSnapshot
        get() = mutableState.value
        set(value) {
            mutableState.value = value
        }

    /** 目前狀態是否包含尚未由平台保存的變更。 */
    private var dirty = false

    /** 狀態實際變更時通知平台 adapter 的非阻塞 callback。 */
    private var dirtyListener: (AuthoritativeStateSnapshot) -> Unit = {}

    /** 取得目前完整狀態的不可變快照。 */
    suspend fun snapshot(): AuthoritativeStateSnapshot = mutex.withLock { currentState }

    /** 取得指定 Room；純讀取不會改變 dirty 狀態。 */
    suspend fun getRoom(id: Uuid): Room? = mutex.withLock { currentState.rooms[id] }

    /** 取得指定 Game；純讀取不會改變 dirty 狀態。 */
    suspend fun getGame(id: Uuid): Game? = mutex.withLock { currentState.games[id] }

    /** 目前是否存在尚未保存的狀態變更。 */
    suspend fun isDirty(): Boolean = mutex.withLock { dirty }

    /** 平台完成保存後清除 dirty flag。 */
    suspend fun markClean() = mutex.withLock { dirty = false }

    /**
     * 登記狀態變更 callback，供平台將對應的存檔容器標記為 dirty。
     *
     * callback 在 store mutex 內同步執行，不得阻塞或再次呼叫 store。
     */
    suspend fun setDirtyListener(listener: (AuthoritativeStateSnapshot) -> Unit) = mutex.withLock {
        dirtyListener = listener
    }

    /**
     * 以相同交易邊界更新政策與外部有效設定；關閉後不恢復已停止場次。
     *
     * @param policy 新場資格政策。
     * @param onApplied 同步發布有效設定的非阻塞 callback；不得重入 store 或執行 I/O。
     */
    suspend fun applyHistoryRecordingPolicy(policy: HistoryRecordingPolicy, onApplied: () -> Unit = {}) = mutex.withLock {
        var recording = restoreDecisions(currentState)
        if (!policy.enabled) {
            currentState.games.values.forEach { game ->
                if (!game.isMatchOver && recording.decisionsByMatchId[game.matchId] == HistoryRecordingDecision.RECORDING) {
                    recording = recording.recordMissing(game).copy(
                        decisionsByMatchId = recording.decisionsByMatchId + (game.matchId to HistoryRecordingDecision.STOPPED_CONFIG_DISABLED),
                    )
                }
            }
        }
        onApplied()
        recordingPolicy = policy
        commit(currentState.copy(historyRecordingState = recording))
    }

    /**
     * 在已持有交易鎖的 repository 中查詢同一份記錄資格，不建立歷史快照。
     *
     * @param snapshot 交易讀取的權威快照。
     * @param game 交易的原有或新建立對局。
     * @return 是否允許為這次交易建立歷史草稿。
     */
    internal fun shouldRecordHistory(snapshot: AuthoritativeStateSnapshot, game: Game): Boolean {
        val decision = snapshot.historyRecordingState.decisionsByMatchId[game.matchId]
            ?: when {
                snapshot.games[game.id]?.matchId != game.matchId -> recordingPolicy.decide(game)
                game.matchId in snapshot.historyRecordingState.nextSequenceByMatchId -> HistoryRecordingDecision.RECORDING
                else -> HistoryRecordingDecision.EXCLUDED_NO_OPENING
            }
        return isHistoryStorageAvailable &&
            (recordingPolicy.enabled || game.isMatchOver) &&
            decision == HistoryRecordingDecision.RECORDING
    }

    /** 更新歷史儲存端容量旗標；不可用期間仍保留權威交易，但不建立待寫事件。
     *
     * @param available 儲存端是否可接受新的歷史事件。
     */
    suspend fun applyHistoryStorageAvailability(available: Boolean) = mutex.withLock {
        isHistoryStorageAvailable = available
        if (available) return@withLock
        var recording = restoreDecisions(currentState)
        currentState.games.values.forEach { game ->
            if (recording.decisionsByMatchId[game.matchId] == HistoryRecordingDecision.RECORDING) {
                recording = recording.recordMissing(game).copy(
                    decisionsByMatchId = recording.decisionsByMatchId +
                        (game.matchId to HistoryRecordingDecision.STOPPED_STORAGE_UNAVAILABLE),
                )
            }
        }
        commit(currentState.copy(historyRecordingState = recording))
    }

    /**
     * 確認已同步的資格診斷，只移除已離開權威狀態且沒有待寫事件的場次。
     *
     * @param matchIds 儲存端已接收診斷或完整封存的場次。
     */
    suspend fun acknowledgeHistoryDecisions(matchIds: Set<Uuid>) = mutex.withLock {
        val recording = currentState.historyRecordingState
        val protected = currentState.games.values.map { it.matchId }.toSet() + recording.pendingEvents.map { it.matchId }
        val removable = matchIds - protected
        commit(currentState.copy(historyRecordingState = recording.copy(decisionsByMatchId = recording.decisionsByMatchId - removable)))
    }

    /** 確認已同步的場次 metadata，僅清除沒有對局與待寫事件保護的項目。
     *
     * @param matchIds 已由儲存端確認的場次 UUID。
     */
    suspend fun acknowledgeHistoryMetadata(matchIds: Set<Uuid>) = mutex.withLock {
        val recording = currentState.historyRecordingState
        val protected = currentState.games.values.map { it.matchId }.toSet() + recording.pendingEvents.map { it.matchId }
        val removable = matchIds - protected
        val next = recording.copy(
            nextSequenceByMatchId = recording.nextSequenceByMatchId - removable,
            firstMissingSequenceByMatchId = recording.firstMissingSequenceByMatchId - removable,
            decisionsByMatchId = recording.decisionsByMatchId - removable,
            terminalByMatchId = recording.terminalByMatchId - removable,
        )
        commit(currentState.copy(historyRecordingState = next))
    }

    /**
     * 確認儲存端已提交的清理收據，避免較舊待寫歷史重新建立已清理紀錄。
     *
     * 只丟棄收據涵蓋且已離開進行中集合的歷史事件；遊戲本身及其他場次不受影響。
     * 可接續的同 ID 對局保留既有待寫資料並停止新增，待真正終止後再確認。
     *
     * @param confirmations 由儲存 adapter 讀取已提交 tombstone 所建立的收據，不得由玩家輸入建立。
     */
    suspend fun acknowledgePrunedHistory(confirmations: Collection<HistoryPruningConfirmation>) = mutex.withLock {
        val confirmed = confirmations.mapTo(mutableSetOf()) { it.matchId }
        if (confirmed.isEmpty()) return@withLock
        val active = currentState.games.values.mapTo(mutableSetOf()) { it.matchId }
        val removable = confirmed - active
        val recording = currentState.historyRecordingState
        val decisions = (recording.decisionsByMatchId - removable).toMutableMap()
        (confirmed intersect active).forEach { decisions[it] = HistoryRecordingDecision.STOPPED_PRUNED }
        commit(
            currentState.copy(
                historyRecordingState = recording.copy(
                    pendingEvents = recording.pendingEvents.filterNot { it.matchId in removable },
                    nextSequenceByMatchId = recording.nextSequenceByMatchId - removable,
                    firstMissingSequenceByMatchId = recording.firstMissingSequenceByMatchId - removable,
                    decisionsByMatchId = decisions,
                    terminalByMatchId = recording.terminalByMatchId - removable,
                ),
            ),
        )
    }

    /**
     * 由現存序號辨識原有記錄；缺少開局證據的對局不從中途新增。
     *
     * @param snapshot 待補足每場判定的權威快照。
     * @return 帶固定資格的待寫歷史狀態。
     */
    private fun restoreDecisions(snapshot: AuthoritativeStateSnapshot): HistoryRecordingState {
        val recording = snapshot.historyRecordingState
        val decisions = recording.decisionsByMatchId.toMutableMap()
        snapshot.games.values.forEach { game ->
            if (game.matchId !in decisions) {
                decisions[game.matchId] = if (game.matchId in recording.nextSequenceByMatchId) {
                    HistoryRecordingDecision.RECORDING
                } else {
                    HistoryRecordingDecision.EXCLUDED_NO_OPENING
                }
            }
        }
        return recording.copy(decisionsByMatchId = decisions)
    }

    /**
     * 發布實際變更並通知持久化 adapter。
     *
     * @param nextState 完成交易的不可變快照。
     */
    private fun commit(nextState: AuthoritativeStateSnapshot) {
        if (nextState != currentState) {
            currentState = nextState
            dirty = true
            dirtyListener(currentState)
        }
    }

    /** 僅確認資料庫已提交的事件 ID，保留同時新增的事件、下一序號與缺口。 */
    suspend fun acknowledgeHistoryEvents(ids: Set<Pair<Uuid, Long>>): Int = mutex.withLock {
        if (ids.isEmpty()) return@withLock 0
        val recording = currentState.historyRecordingState
        val remaining = recording.pendingEvents.filterNot { (it.matchId to it.sequence) in ids }
        val acknowledged = recording.pendingEvents.size - remaining.size
        if (acknowledged > 0) {
            currentState = currentState.copy(historyRecordingState = recording.copy(pendingEvents = remaining))
            dirty = true
            dirtyListener(currentState)
        }
        acknowledged
    }

    /**
     * 載入已保存的完整狀態並視為乾淨；不觸發 dirty callback。
     *
     * @param state 經 schema migration 與 DTO 驗證後的狀態。
     */
    suspend fun load(state: AuthoritativeStateSnapshot) = mutex.withLock {
        currentState = state
        dirty = false
    }

    /**
     * 以原子方式讀取並更新完整伺服器權威狀態。
     *
     * 只有 [AuthoritativeStateUpdate.state] 與目前狀態不同時才會標記 dirty 並通知 listener。
     */
    suspend fun <T> update(
        block: suspend (AuthoritativeStateSnapshot) -> AuthoritativeStateUpdate<T>,
    ): T = mutex.withLock {
        val update = block(currentState)
        var recording = restoreDecisions(update.state)
        update.state.games.values.forEach { game ->
            if (currentState.games[game.id]?.matchId != game.matchId) {
                recording = recording.copy(decisionsByMatchId = recording.decisionsByMatchId + (game.matchId to recordingPolicy.decide(game)))
            }
        }
        val nextState = if (update.state != currentState) {
            val timestamp = historyClock.now().toEpochMilliseconds()
            val previousHistory = currentState.historyRecordingState
            val removedOrReplaced = currentState.games.values.filter { old ->
                val replacement = update.state.games[old.id]
                replacement == null || replacement.matchId != old.matchId
            }
            val terminals = removedOrReplaced.filter { old ->
                old.matchId in previousHistory.nextSequenceByMatchId ||
                    old.matchId in previousHistory.pendingEvents.map { it.matchId } ||
                    old.matchId in previousHistory.firstMissingSequenceByMatchId ||
                    previousHistory.decisionsByMatchId[old.matchId] == HistoryRecordingDecision.RECORDING ||
                    previousHistory.decisionsByMatchId[old.matchId] == HistoryRecordingDecision.STOPPED_CONFIG_DISABLED ||
                    previousHistory.decisionsByMatchId[old.matchId] == HistoryRecordingDecision.STOPPED_STORAGE_UNAVAILABLE
            }.associate { old ->
                old.matchId to HistoryRecordingTerminal(timestamp, old.isMatchOver, old.id)
            }
            recording = recording.copy(terminalByMatchId = recording.terminalByMatchId + terminals)
            if (!isHistoryStorageAvailable) {
                update.state.games.values.forEach { game ->
                    if (currentState.games[game.id] != game &&
                        recording.decisionsByMatchId[game.matchId] == HistoryRecordingDecision.RECORDING
                    ) {
                        recording = recording.recordMissing(game).copy(
                            decisionsByMatchId = recording.decisionsByMatchId +
                                (game.matchId to HistoryRecordingDecision.STOPPED_STORAGE_UNAVAILABLE),
                        )
                    }
                }
            }
            val withDrafts = update.historyDraftsByTableId.entries.fold(recording) { recording, entry ->
                val game = update.state.games[entry.key] ?: currentState.games[entry.key]
                    ?: error("History event references unknown table ${entry.key}")
                val completedReturn = game.isMatchOver && entry.value.all { it.fact is HistoryFact.ReturnedToRoom }
                if (!isHistoryStorageAvailable || (!recordingPolicy.enabled && !completedReturn) || recording.decisionsByMatchId[game.matchId] != HistoryRecordingDecision.RECORDING) return@fold recording
                val before = currentState.games[entry.key]?.tableState
                val after = update.state.games[entry.key]?.tableState
                val hasSnapshot = entry.value.any { it.fact is HistoryFact.MatchStarted || it.fact is HistoryFact.RoundStarted }
                val resultDraft = if (after != null && before != after && !hasSnapshot) {
                    val change = if (before == null) null else runCatching { HistoryTableChange.between(before, after) }.getOrNull()
                    HistoryEventDraft(
                        actorPlayerId = null,
                        fact = HistoryFact.TableChanged(
                            change?.let(HistoryTableResult::Change)
                                ?: HistoryTableResult.Checkpoint("mahjongcraft:unsupported_structural_change", after),
                        ),
                    )
                } else {
                    null
                }
                runCatching { recording.append(game, entry.value + listOfNotNull(resultDraft), timestamp, maxPendingHistoryEvents) }
                    .getOrElse { recording.recordMissing(game) }
            }
            val recordingState = update.historyRecordingFailures.fold(withDrafts) { recording, tableId ->
                val game = update.state.games[tableId] ?: currentState.games[tableId]
                if (game == null || (!recordingPolicy.enabled && !game.isMatchOver) || recording.decisionsByMatchId[game.matchId] != HistoryRecordingDecision.RECORDING) recording else recording.recordMissing(game)
            }
            val active = update.state.games.values.mapTo(mutableSetOf()) { it.matchId }
            update.state.copy(
                historyRecordingState = recordingState.copy(
                    decisionsByMatchId = recordingState.decisionsByMatchId.filter { (matchId, decision) ->
                        matchId in active ||
                            decision == HistoryRecordingDecision.RECORDING ||
                            decision == HistoryRecordingDecision.STOPPED_CONFIG_DISABLED ||
                            decision == HistoryRecordingDecision.STOPPED_STORAGE_UNAVAILABLE ||
                            decision == HistoryRecordingDecision.STOPPED_PRUNED
                    },
                ),
            )
        } else {
            update.state
        }
        commit(nextState)
        update.result
    }
}
