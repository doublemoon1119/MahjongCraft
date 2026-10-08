package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
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

/**
 * 依查詢範圍選擇資料來源；壓力測試範圍只在開發環境且壓力測試資料庫存在時可用。
 *
 * @param formal 正式歷史的資料來源，供自己的對局與全部對局使用。
 * @param stressTest 壓力測試資料來源；沒有壓力測試資料庫時為 null。
 * @param isDevelopment 是否為開發環境。
 * @return 資料來源；壓力測試資料不可用時為 null。
 */
internal fun selectHistoryDataSource(
    scope: HistoryQueryScopeDto,
    formal: HistoryDataSource,
    stressTest: HistoryDataSource?,
    isDevelopment: Boolean,
): HistoryDataSource? = when (scope) {
    HistoryQueryScopeDto.OWN,
    HistoryQueryScopeDto.ALL,
    -> formal

    HistoryQueryScopeDto.STRESS_TEST -> stressTest?.takeIf { isDevelopment }
}

/**
 * 判斷是否可使用壓力測試範圍：與全部對局相同的權限，且開發環境中存在壓力測試資料庫。
 *
 * @param canQueryAll 是否可查詢全部對局。
 * @param isDevelopment 是否為開發環境。
 * @param stressTestAvailable 是否存在壓力測試資料庫。
 */
internal fun canQueryStressTest(canQueryAll: Boolean, isDevelopment: Boolean, stressTestAvailable: Boolean): Boolean = canQueryAll && isDevelopment && stressTestAvailable
