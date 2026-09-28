package com.doublemoon1119.mahjongcraft.flow.server.state

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryCaptureState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
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
 * @property historyCaptureState 與 Game 生命週期分離的待寫歷史事件。
 */
data class AuthoritativeStateSnapshot(
    val rooms: Map<Uuid, Room> = emptyMap(),
    val games: Map<Uuid, Game> = emptyMap(),
    val historyCaptureState: HistoryCaptureState = HistoryCaptureState(),
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
 * @property historyCaptureFailures 歷史事件採集失敗的桌子 ID；提交狀態時為對應場次記錄序號缺口。
 */
data class AuthoritativeStateUpdate<T>(
    val state: AuthoritativeStateSnapshot,
    val result: T,
    val historyDraftsByTableId: Map<Uuid, List<HistoryEventDraft>> = emptyMap(),
    val historyCaptureFailures: Set<Uuid> = emptySet(),
)

/**
 * 以同一把互斥鎖管理 Room 與 Game 的共用狀態儲存。
 *
 * 所有變更皆透過 [update] 提交，使 Room → Game 等跨集合操作能在單次交易內完成。變更後的狀態同時
 * 發布到 [state]，供需要在狀態改變時反應的服務訂閱。
 *
 * @property historyCaptureEnabled 是否將交易內的歷史草稿加入待寫佇列；預設停用。
 * @property historyClock 產生歷史事件 UTC 時間戳的時鐘；事件順序仍由序號決定。
 * @property maxPendingHistoryEvents 待寫佇列的容量上限；超出時只保留序號缺口，不阻塞對局。
 */
@Single
class AuthoritativeStateStore(
    private val historyCaptureEnabled: Boolean = false,
    private val historyClock: Clock = Clock.System,
    private val maxPendingHistoryEvents: Int = 256,
) {
    init {
        require(maxPendingHistoryEvents >= 0) { "Pending history capacity must not be negative" }
    }

    /** 28B 的 writer 接入前不在正式環境累積無界待寫資料。 */
    val isHistoryCaptureEnabled: Boolean get() = historyCaptureEnabled

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
        val nextState = if (historyCaptureEnabled && update.state != currentState) {
            val timestamp = historyClock.now().toEpochMilliseconds()
            val withDrafts = update.historyDraftsByTableId.entries.fold(currentState.historyCaptureState) { capture, entry ->
                val game = update.state.games[entry.key] ?: currentState.games[entry.key]
                    ?: error("History event references unknown table ${entry.key}")
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
                runCatching { capture.append(game, entry.value + listOfNotNull(resultDraft), timestamp, maxPendingHistoryEvents) }
                    .getOrElse { capture.recordMissing(game) }
            }
            val captureState = update.historyCaptureFailures.fold(withDrafts) { capture, tableId ->
                val game = update.state.games[tableId] ?: currentState.games[tableId]
                if (game == null) capture else capture.recordMissing(game)
            }
            update.state.copy(historyCaptureState = captureState)
        } else {
            update.state
        }
        if (nextState != currentState) {
            currentState = nextState
            dirty = true
            dirtyListener(currentState)
        }
        update.result
    }
}
