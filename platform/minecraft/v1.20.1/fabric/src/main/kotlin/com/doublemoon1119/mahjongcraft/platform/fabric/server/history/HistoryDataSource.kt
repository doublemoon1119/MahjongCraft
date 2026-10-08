package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import org.koin.core.annotation.Single

/**
 * 歷史查詢讀取的資料來源。
 *
 * @property writer 持有資料庫連線與 session 的寫入元件。
 * @property store 承載這個來源中進行中對局的權威來源；查詢結果須排除其中仍在進行的對局。
 */
class HistoryDataSource(
    val writer: FabricHistoryOutboxWriter,
    val store: AuthoritativeStateStore,
)

/** 開發環境壓力測試資料庫的歷史資料來源；壓力測試建立資料庫時登記，刪除時清除。 */
@Single
class StressTestHistorySource {
    /** 目前的壓力測試資料來源；沒有壓力測試資料庫時為 null。 */
    @Volatile var current: HistoryDataSource? = null
}
