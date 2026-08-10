package com.doublemoon1119.mahjongcraft.flow.server.state

import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 伺服器目前持有的完整 Room 與 Game 狀態快照。
 *
 * @property rooms 以桌子 UUID 索引的等待階段狀態。
 * @property games 以桌子 UUID 索引的進行中狀態。
 */
data class AuthoritativeStateSnapshot(
    val rooms: Map<Uuid, Room> = emptyMap(),
    val games: Map<Uuid, TableState> = emptyMap(),
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
 */
data class AuthoritativeStateUpdate<T>(
    val state: AuthoritativeStateSnapshot,
    val result: T,
)

/**
 * 以同一把互斥鎖管理 Room 與 Game 的共用狀態儲存。
 *
 * 所有變更皆透過 [update] 提交，使 Room → Game 等跨集合操作能在單次交易內完成。
 */
@Single
class AuthoritativeStateStore {
    /** 保護狀態、dirty flag 與 dirty listener 的互斥鎖。 */
    private val mutex = Mutex()

    /** 目前的不可變伺服器權威狀態。 */
    private var currentState = AuthoritativeStateSnapshot()

    /** 目前狀態是否包含尚未由平台保存的變更。 */
    private var dirty = false

    /** 狀態實際變更時通知平台 adapter 的非阻塞 callback。 */
    private var dirtyListener: () -> Unit = {}

    /** 取得目前完整狀態的不可變快照。 */
    suspend fun snapshot(): AuthoritativeStateSnapshot = mutex.withLock { currentState }

    /** 取得指定 Room；純讀取不會改變 dirty 狀態。 */
    suspend fun getRoom(id: Uuid): Room? = mutex.withLock { currentState.rooms[id] }

    /** 取得指定 Game；純讀取不會改變 dirty 狀態。 */
    suspend fun getGame(id: Uuid): TableState? = mutex.withLock { currentState.games[id] }

    /** 目前是否存在尚未保存的狀態變更。 */
    suspend fun isDirty(): Boolean = mutex.withLock { dirty }

    /** 平台完成保存後清除 dirty flag。 */
    suspend fun markClean() = mutex.withLock { dirty = false }

    /**
     * 登記狀態變更 callback，供平台將對應的存檔容器標記為 dirty。
     *
     * callback 在 store mutex 內同步執行，不得阻塞或再次呼叫 store。
     */
    suspend fun setDirtyListener(listener: () -> Unit) = mutex.withLock { dirtyListener = listener }

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
        if (update.state != currentState) {
            currentState = update.state
            dirty = true
            dirtyListener()
        }
        update.result
    }
}
