package com.doublemoon1119.mahjongcraft.flow.server.state

import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryPruningConfirmation
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingTerminal
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingTransfer
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableChange
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTransferResult
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/**
 * 伺服器目前持有的完整 Room 與 Game 狀態快照。
 *
 * @property rooms 以場地 UUID 索引的等待階段狀態。
 * @property games 以場地 UUID 索引的進行中狀態。
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
            "The same venue ID must not exist as both a room and a game"
        }
    }
}

/**
 * 單次伺服器權威狀態交易的結果。
 *
 * @property state 交易完成後的完整狀態。
 * @property result 回傳給呼叫端的結果。
 * @property historyDraftsByVenueId 與本次狀態變更一起提交的權威事實；提交後一律交給
 *   [AuthoritativeStateStore.setCommittedFactsListener] 登記的接收者，是否寫入歷史由記錄政策決定。
 * @property historyRecordingFailures 歷史事件記錄失敗的場地 ID；提交狀態時為對應場次記錄序號缺口。
 */
data class AuthoritativeStateUpdate<T>(
    val state: AuthoritativeStateSnapshot,
    val result: T,
    val historyDraftsByVenueId: Map<Uuid, List<HistoryEventDraft>> = emptyMap(),
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
 * @property historyRecordingObserver 接收每次交易記錄歷史事件的耗時與事件數；null 時不量測。
 */
@Single
class AuthoritativeStateStore(
    historyRecordingEnabled: Boolean = false,
    private val historyClock: Clock = Clock.System,
    val maxPendingHistoryEvents: Int = 256,
    private val historyRecordingObserver: HistoryRecordingObserver? = null,
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

    /** 已提交事實的接收者；見 [setCommittedFactsListener]。 */
    @Volatile private var committedFactsListener: (CommittedGameFacts) -> Unit = {}

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
     * 登記已提交事實的接收者，取代先前登記的接收者。
     *
     * 每次交易提交後，依場地在 store mutex 內同步呼叫一次，順序與提交順序相同；呼叫發生在提交交易的執行緒上。
     * 與歷史記錄政策及儲存端可用性無關；被拒絕、失敗或沒有改變對局的交易不會呼叫。接收者不得阻塞或再次呼叫
     * store；接收者丟出的例外會被忽略，交易照常成立。store 不保留任何尚未處理的事實。
     *
     * @param listener 接收者；傳入不做事的接收者即可停止接收。
     */
    fun setCommittedFactsListener(listener: (CommittedGameFacts) -> Unit) {
        committedFactsListener = listener
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
            recording.transfersByMatchId.keys.forEach { id ->
                recording = stopTransfer(recording, id, HistoryRecordingDecision.STOPPED_CONFIG_DISABLED)
            }
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

    /** 更新歷史儲存端容量旗標；不可用期間仍保留權威交易，但不建立待寫事件。
     *
     * @param available 儲存端是否可接受新的歷史事件。
     */
    suspend fun applyHistoryStorageAvailability(available: Boolean) = mutex.withLock {
        isHistoryStorageAvailable = available
        if (available) return@withLock
        var recording = restoreDecisions(currentState)
        recording.transfersByMatchId.keys.forEach { id ->
            recording = stopTransfer(recording, id, HistoryRecordingDecision.STOPPED_STORAGE_UNAVAILABLE)
        }
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
     * 永久停止仍在記錄中的 [matchId]，保留缺口；對局被正常流程以外的方式修改（例如開發用指令）時使用。
     *
     * 該場不在權威狀態中或沒有在記錄時不做任何事。
     *
     * @param matchId 欲停止記錄的場次。
     */
    suspend fun stopHistoryRecording(matchId: Uuid) = mutex.withLock {
        val recording = restoreDecisions(currentState)
        val game = currentState.games.values.firstOrNull { it.matchId == matchId } ?: return@withLock
        if (recording.decisionsByMatchId[matchId] != HistoryRecordingDecision.RECORDING) return@withLock
        commit(
            currentState.copy(
                historyRecordingState = recording.recordMissing(game).copy(
                    decisionsByMatchId = recording.decisionsByMatchId + (matchId to HistoryRecordingDecision.STOPPED_EXTERNALLY_MODIFIED),
                ),
            ),
        )
    }

    /**
     * 確認已同步的資格診斷，只移除已離開權威狀態且沒有待寫事件的場次。
     *
     * @param matchIds 儲存端已接收診斷或完整封存的場次。
     */
    suspend fun acknowledgeHistoryDecisions(matchIds: Set<Uuid>) = mutex.withLock {
        val recording = currentState.historyRecordingState
        val protected = currentState.games.values.map { it.matchId }.toSet() + recording.pendingEvents.map { it.matchId } + recording.transfersByMatchId.keys
        val removable = matchIds - protected
        commit(currentState.copy(historyRecordingState = recording.copy(decisionsByMatchId = recording.decisionsByMatchId - removable)))
    }

    /** 確認已同步的場次 metadata，僅清除沒有對局與待寫事件保護的項目。
     *
     * @param matchIds 已由儲存端確認的場次 UUID。
     */
    suspend fun acknowledgeHistoryMetadata(matchIds: Set<Uuid>) = mutex.withLock {
        val recording = currentState.historyRecordingState
        val protected = currentState.games.values.map { it.matchId }.toSet() + recording.pendingEvents.map { it.matchId } + recording.transfersByMatchId.keys
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
        val active = currentState.games.values.mapTo(mutableSetOf()) { it.matchId } + currentState.historyRecordingState.transfersByMatchId.keys
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
     * 載入已保存的完整狀態；隔離來源不會重啟，未完成轉移固定為中止。
     *
     * 一般載入維持乾淨；轉移恢復產生的新終點標記為待保存，但不呼叫前一 session 的 callback。
     *
     * @param state 經 schema migration 與 DTO 驗證後的狀態。
     */
    suspend fun load(state: AuthoritativeStateSnapshot) = mutex.withLock {
        var recording = state.historyRecordingState
        recording.transfersByMatchId.forEach { (id, transfer) ->
            recording = stopTransfer(recording, id, HistoryRecordingDecision.STOPPED_TRANSFER_INTERRUPTED).copy(
                terminalByMatchId = recording.terminalByMatchId + (id to HistoryRecordingTerminal(historyClock.now().toEpochMilliseconds(), false, transfer.venueId)),
            )
        }
        currentState = state.copy(historyRecordingState = recording.copy(transfersByMatchId = emptyMap()))
        dirty = currentState != state
    }

    /**
     * 為隔離的權威來源固定記錄資格，不將來源 Room 或 Game 加入目前狀態。
     *
     * @param game 已走正式開局流程的來源對局。
     * @return 是否建立可接收事件的轉移。
     */
    suspend fun beginHistoryTransfer(game: Game): Boolean = mutex.withLock {
        val recording = currentState.historyRecordingState
        require(game.id !in currentState.games && game.id !in currentState.rooms) { "History transfer venue conflicts with a live venue" }
        require(game.matchId !in recording.nextSequenceByMatchId && game.matchId !in recording.decisionsByMatchId && game.matchId !in recording.transfersByMatchId) {
            "History transfer match already exists"
        }
        require(recording.transfersByMatchId.values.none { it.venueId == game.id }) { "History transfer venue already exists" }
        if (!isHistoryStorageAvailable || recordingPolicy.decide(game) != HistoryRecordingDecision.RECORDING) return@withLock false
        commit(
            currentState.copy(
                historyRecordingState = recording.copy(
                    nextSequenceByMatchId = recording.nextSequenceByMatchId + (game.matchId to 1L),
                    decisionsByMatchId = recording.decisionsByMatchId + (game.matchId to HistoryRecordingDecision.RECORDING),
                    transfersByMatchId = recording.transfersByMatchId + (game.matchId to HistoryRecordingTransfer(game.id)),
                ),
            ),
        )
        true
    }

    /**
     * 接收完整交易批次並保留正常對局的待寫容量；滿載時不推進序號或產生缺口。
     *
     * @param matchId 已建立轉移的場次。
     * @param events 原始權威事件；最多 64 筆，且不得切斷來源交易。
     * @return 接收、等待容量或永久停止的結果。
     */
    suspend fun appendHistoryTransfer(matchId: Uuid, events: List<HistoryOutboxEvent>): HistoryTransferResult = mutex.withLock {
        require(events.isNotEmpty() && events.size <= MAX_TRANSFER_BATCH) { "History transfer batch size must be between 1 and 64" }
        val recording = currentState.historyRecordingState
        val transfer = recording.transfersByMatchId[matchId] ?: error("History transfer is not active")
        require(events.all { it.matchId == matchId && it.venueId == transfer.venueId }) { "History transfer event identity does not match" }
        if (recording.decisionsByMatchId[matchId] != HistoryRecordingDecision.RECORDING || !recordingPolicy.enabled || !isHistoryStorageAvailable) {
            return@withLock HistoryTransferResult.STOPPED
        }
        if (events == transfer.lastAcceptedBatch) return@withLock HistoryTransferResult.ACCEPTED
        val next = recording.nextSequenceByMatchId.getValue(matchId)
        require(events.first().sequence == next && events.zipWithNext().all { (a, b) -> a.sequence + 1 == b.sequence }) {
            "History transfer sequence is not contiguous"
        }
        require(events.first().transactionFirstSequence == next && events.all { it.transactionFirstSequence in next..it.sequence }) {
            "History transfer transaction boundary is invalid"
        }
        require(events.groupBy { it.transactionFirstSequence }.all { (first, transaction) -> transaction.first().sequence == first }) {
            "History transfer transaction is incomplete"
        }
        require(events.zipWithNext().all { (a, b) -> a.transactionFirstSequence <= b.transactionFirstSequence }) {
            "History transfer transactions are not ordered"
        }
        require(next != 1L || events.first().fact is HistoryFact.MatchStarted) { "History transfer must start with match opening" }
        require(events.last().sequence < Long.MAX_VALUE) { "History transfer sequence exhausted" }
        val transferCapacity = minOf(MAX_TRANSFER_BATCH * 2, maxPendingHistoryEvents / 2)
        if (recording.pendingEvents.size + events.size > transferCapacity) return@withLock HistoryTransferResult.WAITING_FOR_CAPACITY
        commit(
            currentState.copy(
                historyRecordingState = recording.copy(
                    pendingEvents = recording.pendingEvents + events,
                    nextSequenceByMatchId = recording.nextSequenceByMatchId + (matchId to events.last().sequence + 1L),
                    transfersByMatchId = recording.transfersByMatchId + (
                        matchId to transfer.copy(
                            lastAcceptedBatch = events.toList(),
                            matchCompleted = transfer.matchCompleted || events.any { it.fact is HistoryFact.MatchCompleted },
                        )
                        ),
                ),
            ),
        )
        HistoryTransferResult.ACCEPTED
    }

    /**
     * 提交來源終點；完整場次須已接收 MatchCompleted 及 ReturnedToRoom。
     *
     * @param matchId 欲結束的隔離轉移。
     * @param terminal 來源的權威終點證據；正常完成時保留原時間戳。
     */
    suspend fun finishHistoryTransfer(matchId: Uuid, terminal: HistoryRecordingTerminal) = mutex.withLock {
        val recording = currentState.historyRecordingState
        val transfer = recording.transfersByMatchId[matchId] ?: return@withLock
        require(terminal.venueId == transfer.venueId) { "History transfer terminal venue does not match" }
        val completed = terminal.completed && recording.decisionsByMatchId[matchId] == HistoryRecordingDecision.RECORDING
        require(!completed || (transfer.matchCompleted && transfer.lastAcceptedBatch.lastOrNull()?.fact is HistoryFact.ReturnedToRoom)) {
            "History transfer cannot finish without complete match evidence"
        }
        val stopped = if (completed) recording else stopTransfer(recording, matchId, HistoryRecordingDecision.STOPPED_TRANSFER_INTERRUPTED)
        commit(
            currentState.copy(
                historyRecordingState = stopped.copy(
                    transfersByMatchId = stopped.transfersByMatchId - matchId,
                    terminalByMatchId = stopped.terminalByMatchId + (matchId to terminal.copy(completed = completed)),
                ),
            ),
        )
    }

    /**
     * 永久停止來源並保留第一個缺口，不覆蓋已確認的停止原因。
     *
     * @param recording 欲更新的歷史狀態。
     * @param matchId 欲停止的轉移。
     * @param decision 停止原因。
     * @return 帶缺口與固定原因的新狀態。
     */
    private fun stopTransfer(recording: HistoryRecordingState, matchId: Uuid, decision: HistoryRecordingDecision): HistoryRecordingState {
        if (recording.decisionsByMatchId[matchId] != HistoryRecordingDecision.RECORDING) return recording
        return recording.copy(
            decisionsByMatchId = recording.decisionsByMatchId + (matchId to decision),
            firstMissingSequenceByMatchId = recording.firstMissingSequenceByMatchId + (matchId to (recording.nextSequenceByMatchId[matchId] ?: 1L)),
        )
    }

    private companion object {
        /** 每次可接收的完整交易事件上限，避免隔離來源占滿共用待寫容量。 */
        const val MAX_TRANSFER_BATCH: Int = 64
    }

    /**
     * 以原子方式讀取並更新完整伺服器權威狀態。
     *
     * 只有 [AuthoritativeStateUpdate.state] 與目前狀態不同時才會標記 dirty 並通知 listener。
     *
     * 新場次只有在同一交易提交 [HistoryFact.MatchStarted] 時才依記錄政策決定資格；沒有開局事實的新場次一律為
     * [HistoryRecordingDecision.EXCLUDED_NO_OPENING]，不從中途建立歷史。
     */
    suspend fun <T> update(
        block: suspend (AuthoritativeStateSnapshot) -> AuthoritativeStateUpdate<T>,
    ): T = mutex.withLock {
        val update = block(currentState)
        var recording = restoreDecisions(update.state)
        update.state.games.values.forEach { game ->
            if (currentState.games[game.id]?.matchId != game.matchId) {
                val hasOpening = update.historyDraftsByVenueId[game.id].orEmpty().any { it.fact is HistoryFact.MatchStarted }
                val decision = if (hasOpening) recordingPolicy.decide(game) else HistoryRecordingDecision.EXCLUDED_NO_OPENING
                recording = recording.copy(decisionsByMatchId = recording.decisionsByMatchId + (game.matchId to decision))
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
                    previousHistory.decisionsByMatchId[old.matchId] == HistoryRecordingDecision.STOPPED_STORAGE_UNAVAILABLE ||
                    previousHistory.decisionsByMatchId[old.matchId] == HistoryRecordingDecision.STOPPED_EXTERNALLY_MODIFIED
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
            val recordingMark = historyRecordingObserver?.let { TimeSource.Monotonic.markNow() }
            val withDrafts = update.historyDraftsByVenueId.entries.fold(recording) { recording, entry ->
                val game = update.state.games[entry.key] ?: currentState.games[entry.key]
                    ?: error("History event references unknown venue ${entry.key}")
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
            if (recordingMark != null && update.historyDraftsByVenueId.isNotEmpty()) {
                val appended = (withDrafts.pendingEvents.size - recording.pendingEvents.size).coerceAtLeast(0)
                historyRecordingObserver.onHistoryRecorded(recordingMark.elapsedNow(), appended)
            }
            val recordingState = update.historyRecordingFailures.fold(withDrafts) { recording, venueId ->
                val game = update.state.games[venueId] ?: currentState.games[venueId]
                if (game == null || (!recordingPolicy.enabled && !game.isMatchOver) || recording.decisionsByMatchId[game.matchId] != HistoryRecordingDecision.RECORDING) recording else recording.recordMissing(game)
            }
            val active = update.state.games.values.mapTo(mutableSetOf()) { it.matchId } + recordingState.transfersByMatchId.keys
            update.state.copy(
                historyRecordingState = recordingState.copy(
                    decisionsByMatchId = recordingState.decisionsByMatchId.filter { (matchId, decision) ->
                        matchId in active ||
                            decision == HistoryRecordingDecision.RECORDING ||
                            decision == HistoryRecordingDecision.STOPPED_CONFIG_DISABLED ||
                            decision == HistoryRecordingDecision.STOPPED_STORAGE_UNAVAILABLE ||
                            decision == HistoryRecordingDecision.STOPPED_TRANSFER_INTERRUPTED ||
                            decision == HistoryRecordingDecision.STOPPED_PRUNED ||
                            decision == HistoryRecordingDecision.STOPPED_EXTERNALLY_MODIFIED
                    },
                ),
            )
        } else {
            update.state
        }
        val previousGames = currentState.games
        commit(nextState)
        update.historyDraftsByVenueId.forEach { (venueId, drafts) ->
            val previousGame = previousGames[venueId]
            val game = nextState.games[venueId]
            if (drafts.isNotEmpty() && (previousGame != null || game != null)) {
                val facts = CommittedGameFacts(venueId, previousGame, game, drafts)
                runCatching { committedFactsListener(facts) }
            }
        }
        update.result
    }
}
