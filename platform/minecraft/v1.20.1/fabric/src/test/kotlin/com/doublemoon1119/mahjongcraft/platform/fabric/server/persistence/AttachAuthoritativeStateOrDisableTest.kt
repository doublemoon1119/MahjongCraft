package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.UnregisteredPersistenceTypeException
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUnavailableException
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [attachAuthoritativeStateOrDisable] 在權威狀態無法讀取時停用麻將功能、而不中斷啟動的測試。 */
class AttachAuthoritativeStateOrDisableTest {
    /** 讀取失敗時不往外拋出，權威狀態改為拒絕寫入，並回報檔案與原因。 */
    @Test
    fun `read failure disables writes and reports the problem`() = runTest {
        val store = AuthoritativeStateStore()
        val status = FabricMahjongDataStatus()

        val attached = attachAuthoritativeStateOrDisable(
            attach = { throw UnregisteredPersistenceTypeException("example:custom_rule") },
            markUnavailable = store::markUnavailable,
            report = status::report,
        )

        assertFalse(attached)
        assertFalse(status.isMahjongAvailable)
        assertEquals(
            expected = listOf(
                MahjongDataProblem(
                    source = MahjongDataSource.AUTHORITATIVE_STATE,
                    file = persistentStateDisplayPath(MahjongAuthoritativePersistentState.STORAGE_KEY),
                    cause = MahjongDataLoadCause.MissingExtension("example:custom_rule"),
                ),
            ),
            actual = status.problems,
        )
        assertFailsWith<AuthoritativeStateUnavailableException> {
            store.update { state -> AuthoritativeStateUpdate(state, Unit) }
        }
    }

    /** 成功接上時維持可寫入，不回報任何問題。 */
    @Test
    fun `successful attach keeps mahjong available`() = runTest {
        val store = AuthoritativeStateStore()
        val status = FabricMahjongDataStatus()

        val attached = attachAuthoritativeStateOrDisable(attach = {}, markUnavailable = store::markUnavailable, report = status::report)

        assertTrue(attached)
        assertTrue(status.isMahjongAvailable)
        assertTrue(store.isAvailable)
    }

    /** 協程取消不視為資料無法讀取，照常往外傳且不停用麻將功能。 */
    @Test
    fun `cancellation is rethrown without disabling mahjong`() = runTest {
        val store = AuthoritativeStateStore()
        val status = FabricMahjongDataStatus()

        assertFailsWith<CancellationException> {
            attachAuthoritativeStateOrDisable(
                attach = { throw CancellationException("test cancellation") },
                markUnavailable = store::markUnavailable,
                report = status::report,
            )
        }

        assertTrue(store.isAvailable)
        assertTrue(status.problems.isEmpty())
    }
}
