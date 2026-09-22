package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [ClientAutomaticControlDraftSession] 的草稿、stale 與 ACK 狀態測試。 */
class ClientAutomaticControlDraftSessionTest {
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

    @Test
    fun `matching accepted completion updates baseline and remembers close intent`() {
        val initial = snapshot(revision = 1L, enabled = emptySet())
        val session = ClientAutomaticControlDraftSession(initial)
        assertTrue(session.replaceDraft(setOf("test:auto")))
        val request = request(initial, setOf("test:auto"))
        assertTrue(session.markSubmitted(request, closeAfterAcceptance = true))
        val accepted = initial.copy(revision = 2L, enabledControlIds = setOf("test:auto"))

        val outcome = session.applyCompletion(completion(request, AutomaticControlUpdateResultKindDto.ACCEPTED, accepted))

        assertNotNull(outcome)
        assertTrue(outcome.accepted)
        assertTrue(outcome.closeAfterAcceptance)
        assertEquals(accepted, session.state().baseline)
        assertFalse(session.state().dirty)
        assertFalse(session.state().pending)
    }

    @Test
    fun `rejected completion keeps draft and does not close`() {
        val initial = snapshot(revision = 1L, enabled = emptySet())
        val session = ClientAutomaticControlDraftSession(initial)
        assertTrue(session.replaceDraft(setOf("test:auto")))
        val request = request(initial, setOf("test:auto"))
        assertTrue(session.markSubmitted(request, closeAfterAcceptance = true))

        val outcome = session.applyCompletion(completion(request, AutomaticControlUpdateResultKindDto.REJECTED, initial))

        assertNotNull(outcome)
        assertFalse(outcome.accepted)
        assertFalse(outcome.closeAfterAcceptance)
        assertEquals(setOf("test:auto"), session.state().enabledControlIds)
        assertEquals(AutomaticControlUpdateResultKindDto.REJECTED, session.state().failure)
        assertFalse(session.state().pending)
    }

    @Test
    fun `unrelated completion cannot finish pending request`() {
        val initial = snapshot()
        val session = ClientAutomaticControlDraftSession(initial)
        val request = request(initial, emptySet())
        assertTrue(session.markSubmitted(request, closeAfterAcceptance = false))
        val unrelated = request.copy(requestId = Uuid.random().toString())

        assertNull(session.applyCompletion(completion(unrelated, AutomaticControlUpdateResultKindDto.ACCEPTED, initial)))
        assertTrue(session.state().pending)
    }

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

    /** 建立支援單一測試控制的權威快照。 */
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

    /** 依 [snapshot] 建立更新請求。 */
    private fun request(snapshot: AutomaticControlSnapshotDto, enabled: Set<String>): AutomaticControlUpdateRequestDto = AutomaticControlUpdateRequestDto(
        requestId = Uuid.random().toString(),
        gameId = snapshot.gameId,
        expectedRevision = snapshot.revision,
        enabledControlIds = enabled,
    )

    /** 建立已配對的 client completion。 */
    private fun completion(
        request: AutomaticControlUpdateRequestDto,
        kind: AutomaticControlUpdateResultKindDto,
        snapshot: AutomaticControlSnapshotDto?,
    ): ClientAutomaticControlCompletion = ClientAutomaticControlCompletion(
        sequence = 1L,
        result = AutomaticControlUpdateResultDto(request.requestId, request.gameId, kind, snapshot),
        authoritativeSnapshot = snapshot,
    )
}
