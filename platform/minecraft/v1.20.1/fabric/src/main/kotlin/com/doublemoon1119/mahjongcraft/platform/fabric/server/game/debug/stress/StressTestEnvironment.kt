package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessStepTimer
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryDataSource
import kotlin.uuid.Uuid

/**
 * 壓力測試專用的資料環境：所有測試對局共用的權威來源、分項計時器，以及依 [StressHistoryMode] 處理歷史的背景工作。
 *
 * 歷史不接觸正式對局的待寫佇列，也不套用正式的保留與記錄政策。
 *
 * @property store 所有測試對局共用的權威來源。
 * @property historyMode 歷史處理方式。
 * @property historyTimer [store] 的歷史記錄觀察者，累計歷史記錄耗時與新加入待寫佇列的事件數；各桌的其他環節由各桌自己的計時器累計。
 * @property writerTimer 累計歷史背景工作各環節耗時與已處理事件數。
 * @property sink 處理待寫佇列的背景工作。
 */
class StressTestEnvironment internal constructor(
    val store: AuthoritativeStateStore,
    val historyMode: StressHistoryMode,
    val historyTimer: HeadlessStepTimer,
    val writerTimer: StressWriterTimer,
    private val sink: StressHistorySink,
) {
    /** 對局歷史畫面讀取壓力測試紀錄的資料來源；只有寫進資料庫的模式才有。 */
    val historySource: HistoryDataSource? = (sink as? StressHistorySink.Database)?.let { HistoryDataSource(it.writer, store) }

    /**
     * 處理歷史的背景工作是否發生錯誤或暫停。
     *
     * @return 背景工作已失敗或暫停時為 `true`。
     */
    suspend fun historyFailed(): Boolean = sink.failed()

    /** 停止背景工作；有資料庫時一併刪除資料庫及其附屬檔案。 */
    suspend fun closeAndDelete() = sink.close()
}

/**
 * 以同一筆交易把多桌從共用權威來源移除。
 *
 * @param store 這些桌所在的共用權威來源。
 * @param venueIds 要移除的桌的場地識別碼。
 */
internal suspend fun discardStressTables(store: AuthoritativeStateStore, venueIds: Collection<Uuid>) {
    if (venueIds.isEmpty()) return
    store.update { state ->
        AuthoritativeStateUpdate(state.copy(rooms = state.rooms - venueIds.toSet(), games = state.games - venueIds.toSet()), Unit)
    }
}
