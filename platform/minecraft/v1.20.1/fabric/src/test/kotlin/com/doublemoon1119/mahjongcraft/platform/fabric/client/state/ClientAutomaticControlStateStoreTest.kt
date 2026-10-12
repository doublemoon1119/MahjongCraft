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
    /** 相同對局不接受較舊或同修訂快照。 */
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

    /** 新對局可用較低修訂取代舊快照並清除待處理要求。 */
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

    /** 待處理要求不可被替換，匹配回覆會清除要求並更新快照。 */
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

    /** 不同對局即使要求識別碼相同，也不能清除待處理要求。 */
    @Test
    fun `matching request id from another game does not clear pending request`() {
        val store = ClientAutomaticControlStateStore()
        val pending = request("same-id")
        assertTrue(store.trySetPendingRequest(pending))
        val otherGameResult = AutomaticControlUpdateResultDto(
            requestId = pending.requestId,
            gameId = Uuid.random().toString(),
            result = AutomaticControlUpdateResultKindDto.UNAVAILABLE,
            snapshot = null,
        )

        assertFalse(store.applyResult(otherGameResult))
        assertEquals(pending, store.pendingRequest())
    }

    /** 匹配回覆不以較舊快照覆蓋較新的本機快照。 */
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

    /** 清除狀態時移除快照與要求，但通知修訂保持單調遞增。 */
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

    /**
     * 建立測試用自動控制更新要求。
     *
     * @param id 要求識別碼。
     * @return 使用新對局識別碼及空控制集合的要求。
     */
    private fun request(id: String): AutomaticControlUpdateRequestDto = AutomaticControlUpdateRequestDto(
        requestId = id,
        gameId = Uuid.random().toString(),
        expectedRevision = 0L,
        enabledControlIds = emptySet(),
    )

    /**
     * 建立測試用自動控制快照。
     *
     * @param gameId 對局識別碼。
     * @param revision 快照修訂。
     * @param enabled 已啟用控制識別碼。
     * @return 支援及啟用集合相同的快照。
     */
    private fun snapshot(gameId: String, revision: Long, enabled: Set<String>): AutomaticControlSnapshotDto = AutomaticControlSnapshotDto(
        gameId = gameId,
        revision = revision,
        supportedControlIds = enabled,
        enabledControlIds = enabled,
    )

    /**
     * 建立測試用已接受結果。
     *
     * @param requestId 要求識別碼。
     * @param gameId 對局識別碼。
     * @param snapshot 可選的回覆快照。
     * @return 已接受的更新結果 DTO。
     */
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
