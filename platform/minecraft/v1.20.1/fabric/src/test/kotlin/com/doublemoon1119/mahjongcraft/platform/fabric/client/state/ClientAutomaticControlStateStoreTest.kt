package com.doublemoon1119.mahjongcraft.platform.fabric.client.state

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [ClientAutomaticControlStateStore] 的 client 暫存生命週期與 revision 規則。 */
class ClientAutomaticControlStateStoreTest {
    @Test
    fun `same game rejects an older or equal snapshot`() {
        val store = ClientAutomaticControlStateStore()
        val gameId = Uuid.random().toString()
        val newer = snapshot(gameId, 4L, setOf("auto:new"))

        assertTrue(store.applySnapshot(newer))
        assertFalse(store.applySnapshot(snapshot(gameId, 3L, setOf("auto:old"))))
        assertFalse(store.applySnapshot(newer))
        assertEquals(newer, store.snapshot())
    }

    @Test
    fun `different game replaces the snapshot even with a lower revision`() {
        val store = ClientAutomaticControlStateStore()
        val firstGame = Uuid.random().toString()
        val secondGame = Uuid.random().toString()
        store.applySnapshot(snapshot(firstGame, 9L, setOf("auto:first")))
        assertTrue(store.trySetPendingRequest(request("old-game").copy(gameId = firstGame)))

        val replacement = snapshot(secondGame, 1L, setOf("auto:second"))
        assertTrue(store.applySnapshot(replacement))
        assertEquals(replacement, store.snapshot())
        assertNull(store.pendingRequest())
    }

    @Test
    fun `pending request cannot be replaced and matching result clears it`() {
        val store = ClientAutomaticControlStateStore()
        val first = request("first")
        val second = request("second")
        assertTrue(store.trySetPendingRequest(first))
        assertFalse(store.trySetPendingRequest(second))

        assertEquals(first, store.pendingRequest())
        assertFalse(store.applyResult(result(second.requestId, second.gameId, null)))
        assertEquals(first, store.pendingRequest())
        assertTrue(store.applyResult(result(first.requestId, first.gameId, snapshot(first.gameId, 2L, setOf("auto:accepted")))))
        assertNull(store.pendingRequest())
        assertEquals(setOf("auto:accepted"), store.snapshot()?.enabledControlIds)
    }

    @Test
    fun `matching request id from another game does not clear pending request`() {
        val store = ClientAutomaticControlStateStore()
        val pending = request("same-id")
        assertTrue(store.trySetPendingRequest(pending))
        val otherGameResult = AutomaticControlUpdateResultDto(
            requestId = pending.requestId,
            gameId = Uuid.random().toString(),
            result = AutomaticControlUpdateResultKindDto.UNAVAILABLE,
        )

        assertFalse(store.applyResult(otherGameResult))
        assertEquals(pending, store.pendingRequest())
    }

    @Test
    fun `matching result cannot overwrite a newer local snapshot with stale snapshot`() {
        val store = ClientAutomaticControlStateStore()
        val request = request("request")
        assertTrue(store.trySetPendingRequest(request))
        val current = snapshot(request.gameId, 5L, setOf("auto:current"))
        store.applySnapshot(current)

        assertTrue(store.applyResult(result(request.requestId, request.gameId, snapshot(request.gameId, 4L, setOf("auto:stale")))))
        assertEquals(current, store.snapshot())
        assertNull(store.pendingRequest())
    }

    @Test
    fun `clear removes snapshot and pending request but keeps notification monotonicity`() {
        val store = ClientAutomaticControlStateStore()
        store.applySnapshot(snapshot(Uuid.random().toString(), 1L, emptySet()))
        assertTrue(store.trySetPendingRequest(request("request")))
        val revisionBeforeClear = store.notificationRevision()

        store.clear()

        assertNull(store.snapshot())
        assertNull(store.pendingRequest())
        assertTrue(store.notificationRevision() > revisionBeforeClear)
        val revisionAfterClear = store.notificationRevision()
        store.clear()
        assertEquals(revisionAfterClear, store.notificationRevision())
    }

    private fun request(id: String): AutomaticControlUpdateRequestDto = AutomaticControlUpdateRequestDto(
        requestId = id,
        gameId = Uuid.random().toString(),
        expectedRevision = 0L,
        enabledControlIds = emptySet(),
    )

    private fun snapshot(gameId: String, revision: Long, enabled: Set<String>): AutomaticControlSnapshotDto = AutomaticControlSnapshotDto(
        gameId = gameId,
        revision = revision,
        supportedControlIds = enabled,
        enabledControlIds = enabled,
    )

    private fun result(
        requestId: String,
        gameId: String,
        snapshot: AutomaticControlSnapshotDto?,
    ): AutomaticControlUpdateResultDto = AutomaticControlUpdateResultDto(
        requestId = requestId,
        gameId = gameId,
        result = AutomaticControlUpdateResultKindDto.ACCEPTED,
        snapshot = snapshot,
    )
}
