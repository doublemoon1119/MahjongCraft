package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [ClientAutomaticControlDraftSession] 的草稿、stale 與 ACK 狀態測試。 */
class ClientAutomaticControlDraftSessionTest {
    /** 未修改的草稿工作階段會跟隨較新的權威快照。 */
    @Test
    fun `clean session follows newer authority snapshot`() {
        val initial = snapshot(revision = 1L, enabled = emptySet())
        val session = ClientAutomaticControlDraftSession(initial)
        val newer = initial.copy(revision = 2L, enabledControlIds = setOf("test:auto"))

        session.applySnapshot(newer)

        assertEquals(newer, session.state().baseline)
        assertEquals(setOf("test:auto"), session.state().enabledControlIds)
        assertFalse(session.state().dirty)
        assertFalse(session.state().stale)
    }

    /** 已修改草稿保留本地選擇，外部更新使其標記為過期。 */
    @Test
    fun `dirty session preserves draft and becomes stale on external update`() {
        val initial = snapshot(revision = 1L, enabled = emptySet())
        val session = ClientAutomaticControlDraftSession(initial)
        assertTrue(session.replaceDraft(setOf("test:auto")))

        session.applySnapshot(initial.copy(revision = 2L))

        assertEquals(setOf("test:auto"), session.state().enabledControlIds)
        assertTrue(session.state().dirty)
        assertTrue(session.state().stale)
        assertTrue(session.undo())
        assertEquals(2L, session.state().baseline?.revision)
        assertFalse(session.state().dirty)
    }

    /** 配對的接受結果更新基準快照並保留關閉意圖。 */
    @Test
    fun `matching accepted completion updates baseline and remembers close intent`() {
        val initial = snapshot(revision = 1L, enabled = emptySet())
        val session = ClientAutomaticControlDraftSession(initial)
        assertTrue(session.replaceDraft(setOf("test:auto")))
        val request = request(initial, setOf("test:auto"))
        assertTrue(session.markSubmitted(request, closeAfterAcceptance = true))
        val accepted = initial.copy(revision = 2L, enabledControlIds = setOf("test:auto"))

        val outcome = session.applyCompletion(completion(request, ClientAutomaticControlResultKind.ACCEPTED, accepted))

        assertNotNull(outcome)
        assertTrue(outcome.accepted)
        assertTrue(outcome.closeAfterAcceptance)
        assertEquals(accepted, session.state().baseline)
        assertFalse(session.state().dirty)
        assertFalse(session.state().pending)
    }

    /** 拒絕結果保留草稿且不觸發關閉。 */
    @Test
    fun `rejected completion keeps draft and does not close`() {
        val initial = snapshot(revision = 1L, enabled = emptySet())
        val session = ClientAutomaticControlDraftSession(initial)
        assertTrue(session.replaceDraft(setOf("test:auto")))
        val request = request(initial, setOf("test:auto"))
        assertTrue(session.markSubmitted(request, closeAfterAcceptance = true))

        val outcome = session.applyCompletion(completion(request, ClientAutomaticControlResultKind.REJECTED, initial))

        assertNotNull(outcome)
        assertFalse(outcome.accepted)
        assertFalse(outcome.closeAfterAcceptance)
        assertEquals(setOf("test:auto"), session.state().enabledControlIds)
        assertEquals(ClientAutomaticControlResultKind.REJECTED, session.state().failure)
        assertFalse(session.state().pending)
    }

    /** 重新同步確認沒有生效時保留草稿，不標記過期，可以直接再送出一次。 */
    @Test
    fun `not applied completion keeps draft ready to resubmit`() {
        val initial = snapshot(revision = 1L, enabled = emptySet())
        val session = ClientAutomaticControlDraftSession(initial)
        assertTrue(session.replaceDraft(setOf("test:auto")))
        val request = request(initial, setOf("test:auto"))
        assertTrue(session.markSubmitted(request, closeAfterAcceptance = false))

        assertNotNull(session.applyCompletion(completion(request, ClientAutomaticControlResultKind.NOT_APPLIED, initial)))

        assertEquals(ClientAutomaticControlResultKind.NOT_APPLIED, session.state().failure)
        assertFalse(session.state().stale)
        assertTrue(session.markSubmitted(request(initial, setOf("test:auto")), closeAfterAcceptance = false))
    }

    /** 無法確認是否生效時標記過期，需要復原載入最新狀態後才能再送出。 */
    @Test
    fun `unconfirmed completion marks draft stale`() {
        val initial = snapshot(revision = 1L, enabled = emptySet())
        val session = ClientAutomaticControlDraftSession(initial)
        assertTrue(session.replaceDraft(setOf("test:auto")))
        val request = request(initial, setOf("test:auto"))
        assertTrue(session.markSubmitted(request, closeAfterAcceptance = true))

        val outcome = assertNotNull(session.applyCompletion(completion(request, ClientAutomaticControlResultKind.UNCONFIRMED, initial)))

        assertFalse(outcome.closeAfterAcceptance)
        assertTrue(session.state().stale)
        assertFalse(session.markSubmitted(request(initial, setOf("test:auto")), closeAfterAcceptance = false))
    }

    /** 無關的完成結果不能結束目前 pending 的請求。 */
    @Test
    fun `unrelated completion cannot finish pending request`() {
        val initial = snapshot()
        val session = ClientAutomaticControlDraftSession(initial)
        val request = request(initial, emptySet())
        assertTrue(session.markSubmitted(request, closeAfterAcceptance = false))
        val unrelated = request.copy(requestId = Uuid.random().toString())

        assertNull(session.applyCompletion(completion(unrelated, ClientAutomaticControlResultKind.ACCEPTED, initial)))
        assertTrue(session.state().pending)
    }

    /** 對局切換重置生命週期，clear 移除先前狀態。 */
    @Test
    fun `different game replaces lifecycle and clear removes previous state`() {
        val initial = snapshot(revision = 4L, enabled = setOf("test:auto"))
        val session = ClientAutomaticControlDraftSession(initial)
        val replacement = snapshot(revision = 1L, enabled = emptySet())

        session.applySnapshot(replacement)

        assertEquals(replacement, session.state().baseline)
        assertFalse(session.state().dirty)
        session.clear()
        assertTrue(session.state().unavailable)
        assertTrue(session.state().enabledControlIds.isEmpty())
    }

    /**
     * 建立支援單一測試控制的權威快照。
     *
     * @param gameId 快照所屬對局 ID。
     * @param revision 對局內的快照版本。
     * @param enabled 已啟用的測試控制項 ID。
     * @return 支援 `test:auto` 的權威快照。
     */
    private fun snapshot(
        gameId: String = Uuid.random().toString(),
        revision: Long = 1L,
        enabled: Set<String> = emptySet(),
    ): AutomaticControlSnapshotDto = AutomaticControlSnapshotDto(
        gameId = gameId,
        revision = revision,
        supportedControlIds = setOf("test:auto"),
        enabledControlIds = enabled,
    )

    /**
     * 依 [snapshot] 建立更新請求。
     *
     * @param snapshot 請求所依據的權威快照。
     * @param enabled 請求欲啟用的控制項 ID。
     * @return 使用該快照對局與版本的更新請求。
     */
    private fun request(snapshot: AutomaticControlSnapshotDto, enabled: Set<String>): AutomaticControlUpdateRequestDto = AutomaticControlUpdateRequestDto(
        requestId = Uuid.random().toString(),
        gameId = snapshot.gameId,
        expectedRevision = snapshot.revision,
        enabledControlIds = enabled,
    )

    /**
     * 建立與請求配對的客戶端完成事件。
     *
     * @param request 被完成的更新請求。
     * @param kind 請求最後的結果種類。
     * @param snapshot 完成時的權威快照，若無則為 `null`。
     * @return 與請求 ID 和對局 ID 配對的完成事件。
     */
    private fun completion(
        request: AutomaticControlUpdateRequestDto,
        kind: ClientAutomaticControlResultKind,
        snapshot: AutomaticControlSnapshotDto?,
    ): ClientAutomaticControlCompletion = ClientAutomaticControlCompletion(
        sequence = 1L,
        requestId = request.requestId,
        gameId = request.gameId,
        result = kind,
        authoritativeSnapshot = snapshot,
    )
}
