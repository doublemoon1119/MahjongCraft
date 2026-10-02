package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryRequestDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/** 聊天入口只受連線身分限制，不需要保存完成證據。 */
class ClientHistoryChatEntryStoreTest {
    /** 有效對局在保存完成前即可開啟，切換連線後舊入口失效。 */
    @Test
    fun `test chat entry opens before archival and rejects another session`() {
        val transport = FakeTransport()
        val store = ClientHistoryChatEntryStore(transport)
        val matchId = Uuid.random().toString()
        val token = store.createCommand(matchId).substringAfterLast(' ')
        var opened: String? = null
        assertTrue(store.open(token) { opened = it })
        assertEquals(matchId, opened)
        transport.sessionRevision.value++
        assertFalse(store.open(token) { error("A stale entry must not open history") })
    }

    /** 缺少或無效對局識別仍開一般列表，不將任意輸入寫入命令。 */
    @Test
    fun `test invalid match ids open the general list without command injection`() {
        val store = ClientHistoryChatEntryStore(FakeTransport())
        val command = store.createCommand("invalid /server-command")
        assertFalse(command.contains("server-command"))
        assertTrue(store.open(command.substringAfterLast(' ')) { assertEquals(null, it) })
    }

    /** 超過容量移除最舊入口，斷線清理也失效所有入口。 */
    @Test
    fun `test chat entries are bounded and cleared`() {
        val store = ClientHistoryChatEntryStore(FakeTransport())
        val first = store.createCommand(null).substringAfterLast(' ')
        var last = first
        repeat(64) { last = store.createCommand(null).substringAfterLast(' ') }
        assertFalse(store.open(first) {})
        assertTrue(store.open(last) {})
        store.clear()
        assertFalse(store.open(last) {})
    }

    /** 不執行網路要求的連線版本替身。 */
    private class FakeTransport : HistoryQueryTransport {
        /** 模擬尚未查詢的狀態。 */
        override val state = MutableStateFlow<ClientHistoryQueryState>(ClientHistoryQueryState.Idle)

        /** 測試可切換的連線版本。 */
        override val sessionRevision = MutableStateFlow(0L)

        /** 聊天入口替身使用伺服器預設查詢冷卻。 */
        override val minimumInterval: StateFlow<Duration> = MutableStateFlow(250.milliseconds)

        /** 聊天入口不得自行查詢列表。 */
        override fun queryList(request: HistoryListRequestDto): String = error("Chat entries must not query lists directly")

        /** 聊天入口不得自行查詢摘要。 */
        override fun querySummary(request: HistorySummaryRequestDto): String = error("Chat entries must not query summaries directly")

        /** 聊天入口不得自行查詢規則設定。 */
        override fun queryRuleSettings(request: HistoryRuleSettingsRequestDto): String = error("Chat entries must not query rule settings directly")

        /** 此替身不擁有在途要求。 */
        override fun cancel(requestId: String): Boolean = false
    }
}
