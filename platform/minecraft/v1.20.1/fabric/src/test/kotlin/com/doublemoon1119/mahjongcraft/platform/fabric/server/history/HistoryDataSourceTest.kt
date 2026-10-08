package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 驗證歷史查詢依範圍選擇資料來源，且壓力測試資料只在開發環境開放。 */
class HistoryDataSourceTest {
    private val formal = source()
    private val stressTest = source()

    /** 自己的對局與全部對局都讀正式歷史，即使壓力測試資料存在。 */
    @Test
    fun `own and all scopes read the formal history`() {
        assertSame(formal, selectHistoryDataSource(HistoryQueryScopeDto.OWN, formal, stressTest, isDevelopment = true))
        assertSame(formal, selectHistoryDataSource(HistoryQueryScopeDto.ALL, formal, stressTest, isDevelopment = true))
    }

    /** 壓力測試範圍在開發環境讀壓力測試資料。 */
    @Test
    fun `stress test scope reads the stress test history in development`() {
        assertSame(stressTest, selectHistoryDataSource(HistoryQueryScopeDto.STRESS_TEST, formal, stressTest, isDevelopment = true))
    }

    /** 非開發環境或沒有壓力測試資料庫時，壓力測試範圍沒有資料來源，也不退回正式歷史。 */
    @Test
    fun `stress test scope is unavailable outside development or without a database`() {
        assertNull(selectHistoryDataSource(HistoryQueryScopeDto.STRESS_TEST, formal, stressTest, isDevelopment = false))
        assertNull(selectHistoryDataSource(HistoryQueryScopeDto.STRESS_TEST, formal, stressTest = null, isDevelopment = true))
    }

    /** 壓力測試範圍需要全部對局的權限、開發環境與壓力測試資料庫三者同時成立。 */
    @Test
    fun `stress test scope requires all-match access in development with a database`() {
        assertTrue(canQueryStressTest(canQueryAll = true, isDevelopment = true, stressTestAvailable = true))
        assertFalse(canQueryStressTest(canQueryAll = false, isDevelopment = true, stressTestAvailable = true))
        assertFalse(canQueryStressTest(canQueryAll = true, isDevelopment = false, stressTestAvailable = true))
        assertFalse(canQueryStressTest(canQueryAll = true, isDevelopment = true, stressTestAvailable = false))
    }

    /** 建立一個未連線的資料來源。 */
    private fun source(): HistoryDataSource {
        val store = AuthoritativeStateStore()
        return HistoryDataSource(
            writer = FabricHistoryOutboxWriter(
                store = store,
                registries = bundledPersistenceRegistries(),
                replayProjectionRegistry = buildTestHistoryReplayProjectionRegistry(),
                json = Json,
                dispatchers = TestDispatchers,
                moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() },
                locations = TableLocationRegistry(),
                configState = MinecraftServerConfigState(MinecraftServerConfig()),
                retentionCoordinator = HistoryRetentionCoordinator(),
            ),
            store = store,
        )
    }

    /** 測試使用的 dispatcher 集合。 */
    private object TestDispatchers : CoroutineDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val main: CoroutineDispatcher = Dispatchers.Default
    }
}
