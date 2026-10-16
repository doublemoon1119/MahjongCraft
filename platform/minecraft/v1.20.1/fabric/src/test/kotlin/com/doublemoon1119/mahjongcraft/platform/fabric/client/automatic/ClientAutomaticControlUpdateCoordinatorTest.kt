package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientAutomaticControlStateStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [ClientAutomaticControlUpdateCoordinator] 的送出、配對與生命週期測試。 */
class ClientAutomaticControlUpdateCoordinatorTest {
    /** 送出以權威快照版本為基礎的完整替換請求。 */
    @Test
    fun `submit sends a complete replacement based on authoritative snapshot`() {
        val fixture = fixture()
        val snapshot = snapshot(revision = 7L, supported = setOf("test:auto_a", "test:auto_b"))
        fixture.coordinator.applySnapshot(snapshot)

        val submitted = assertIs<ClientAutomaticControlSubmitResult.Submitted>(
            value = fixture.coordinator.submit(setOf("test:auto_b")),
        )

        assertEquals(snapshot.gameId, submitted.request.gameId)
        assertEquals(7L, submitted.request.expectedRevision)
        assertEquals(setOf("test:auto_b"), submitted.request.enabledControlIds)
        assertEquals(listOf(submitted.request), fixture.sender.requests)
        assertEquals(submitted.request, fixture.coordinator.pendingRequest())
    }

    /** 不可用、不支援或已有請求時拒絕更新且不重複傳送。 */
    @Test
    fun `submit rejects unavailable unsupported and concurrent updates without sending`() {
        val fixture = fixture()
        assertIs<ClientAutomaticControlSubmitResult.Unavailable>(fixture.coordinator.submit(emptySet()))
        fixture.coordinator.applySnapshot(snapshot(supported = setOf("test:auto_a")))
        val unsupported = assertIs<ClientAutomaticControlSubmitResult.Unsupported>(
            fixture.coordinator.submit(setOf("test:unknown")),
        )
        assertEquals(setOf("test:unknown"), unsupported.controlIds)
        val submitted = assertIs<ClientAutomaticControlSubmitResult.Submitted>(
            value = fixture.coordinator.submit(setOf("test:auto_a")),
        )
        assertIs<ClientAutomaticControlSubmitResult.Pending>(fixture.coordinator.submit(emptySet()))
        assertEquals(listOf(submitted.request), fixture.sender.requests)
    }

    /** 同步傳送失敗後還原 pending 狀態。 */
    @Test
    fun `send failure restores pending state`() {
        val failure = IllegalStateException("network unavailable")
        val fixture = fixture(failure)
        fixture.coordinator.applySnapshot(snapshot(supported = setOf("test:auto_a")))

        val result = assertIs<ClientAutomaticControlSubmitResult.SendFailed>(
            value = fixture.coordinator.submit(setOf("test:auto_a")),
        )

        assertSame(failure, result.cause)
        assertNull(fixture.coordinator.pendingRequest())
    }

    /** 僅配對結果建立 request 範圍的完成事件。 */
    @Test
    fun `only matching result creates a request scoped completion`() {
        val fixture = fixture()
        fixture.coordinator.applySnapshot(snapshot(revision = 2L, supported = setOf("test:auto_a")))
        val request = assertIs<ClientAutomaticControlSubmitResult.Submitted>(
            value = fixture.coordinator.submit(setOf("test:auto_a")),
        ).request
        val unrelated = result(request.copy(requestId = "unrelated"), AutomaticControlUpdateResultKindDto.ACCEPTED)

        assertTrue(!fixture.coordinator.applyResult(unrelated))
        assertNull(fixture.coordinator.takeCompletion(request.requestId))
        val acceptedSnapshot = snapshot(
            gameId = request.gameId,
            revision = 3L,
            supported = setOf("test:auto_a"),
            enabled = setOf("test:auto_a"),
        )
        assertTrue(
            fixture.coordinator.applyResult(
                result(request, AutomaticControlUpdateResultKindDto.ACCEPTED, acceptedSnapshot),
            ),
        )
        val completion = assertNotNull(fixture.coordinator.takeCompletion(request.requestId))
        assertEquals(ClientAutomaticControlResultKind.ACCEPTED, completion.result)
        assertEquals(acceptedSnapshot, completion.authoritativeSnapshot)
        assertNull(fixture.coordinator.takeCompletion(request.requestId))
        assertNull(fixture.coordinator.takeCompletion("another-request"))
    }

    /** 清除權威快照、待處理請求與完成事件。 */
    @Test
    fun `clear removes authority pending and completion`() {
        val fixture = fixture()
        fixture.coordinator.applySnapshot(snapshot(supported = setOf("test:auto_a")))
        val request = assertIs<ClientAutomaticControlSubmitResult.Submitted>(
            value = fixture.coordinator.submit(setOf("test:auto_a")),
        ).request
        fixture.coordinator.applyResult(result(request, AutomaticControlUpdateResultKindDto.REJECTED))
        assertNotNull(fixture.coordinator.takeCompletion(request.requestId))
        fixture.coordinator.applySnapshot(snapshot(gameId = request.gameId, revision = 2L, supported = setOf("test:auto_a")))
        val nextRequest = assertIs<ClientAutomaticControlSubmitResult.Submitted>(
            fixture.coordinator.submit(emptySet()),
        ).request
        fixture.coordinator.applyResult(result(nextRequest, AutomaticControlUpdateResultKindDto.REJECTED))

        fixture.coordinator.clear()

        assertNull(fixture.coordinator.snapshot())
        assertNull(fixture.coordinator.pendingRequest())
        assertNull(fixture.coordinator.takeCompletion(nextRequest.requestId))
    }

    /** 桌子移除時清除匹配狀態，延遲 ACK 不得重新建立完成事件。 */
    @Test
    fun `clear table rejects delayed ack and preserves another table`() {
        val fixture = fixture()
        val firstSnapshot = snapshot(supported = setOf("test:auto_a"))
        fixture.coordinator.applySnapshot(firstSnapshot)
        val firstRequest = assertIs<ClientAutomaticControlSubmitResult.Submitted>(
            value = fixture.coordinator.submit(setOf("test:auto_a")),
        ).request

        fixture.coordinator.clearTable(Uuid.parse(firstRequest.gameId))

        assertNull(fixture.coordinator.snapshot())
        assertNull(fixture.coordinator.pendingRequest())
        assertTrue(!fixture.coordinator.applyResult(result(firstRequest, AutomaticControlUpdateResultKindDto.ACCEPTED)))
        assertNull(fixture.coordinator.takeCompletion(firstRequest.requestId))

        val secondSnapshot = snapshot(supported = setOf("test:auto_b"))
        fixture.coordinator.applySnapshot(secondSnapshot)
        val secondRequest = assertIs<ClientAutomaticControlSubmitResult.Submitted>(
            value = fixture.coordinator.submit(setOf("test:auto_b")),
        ).request
        assertTrue(fixture.coordinator.applyResult(result(secondRequest, AutomaticControlUpdateResultKindDto.ACCEPTED)))

        fixture.coordinator.clearTable(Uuid.random())

        assertEquals(secondSnapshot, fixture.coordinator.snapshot())
        assertNotNull(fixture.coordinator.takeCompletion(secondRequest.requestId))
    }

    /** 新對局快照到達後，舊對局結果不能完成請求。 */
    @Test
    fun `old game result cannot complete a request after a new game snapshot`() {
        val fixture = fixture()
        fixture.coordinator.applySnapshot(snapshot(supported = setOf("test:auto_a")))
        val oldRequest = assertIs<ClientAutomaticControlSubmitResult.Submitted>(
            value = fixture.coordinator.submit(setOf("test:auto_a")),
        ).request
        val newGame = snapshot(supported = setOf("test:auto_b"))

        fixture.coordinator.applySnapshot(newGame)

        assertNull(fixture.coordinator.pendingRequest())
        assertEquals(newGame, fixture.coordinator.snapshot())
        assertTrue(!fixture.coordinator.applyResult(result(oldRequest, AutomaticControlUpdateResultKindDto.ACCEPTED)))
        assertNull(fixture.coordinator.takeCompletion(oldRequest.requestId))
    }

    /** 在逾時前收到回覆時不重新同步。 */
    @Test
    fun `a reply before the timeout needs no resync`() {
        val fixture = fixture()
        fixture.coordinator.applySnapshot(snapshot(supported = setOf("test:auto_a")))
        val request = submitted(fixture, setOf("test:auto_a"))

        fixture.advance(ClientAutomaticControlUpdateCoordinator.CONFIRMATION_TIMEOUT.inWholeMilliseconds - 1)
        fixture.coordinator.checkTimeout()
        assertTrue(fixture.coordinator.applyResult(result(request, AutomaticControlUpdateResultKindDto.ACCEPTED)))
        fixture.advance(TIMEOUT_AND_RESYNC_MILLIS)
        fixture.coordinator.checkTimeout()

        assertEquals(0, fixture.sender.snapshotRequests)
        assertEquals(ClientAutomaticControlResultKind.ACCEPTED, fixture.coordinator.takeCompletion(request.requestId)?.result)
    }

    /** 請求被丟棄時：逾時後要求一次重新同步，快照版本沒有前進就判定沒有生效，之後可以重新送出。 */
    @Test
    fun `dropped update resyncs and reports not applied so it can be resubmitted`() {
        val fixture = fixture()
        val authority = snapshot(revision = 4L, supported = setOf("test:auto_a"))
        fixture.coordinator.applySnapshot(authority)
        val request = submitted(fixture, setOf("test:auto_a"))

        fixture.advance(ClientAutomaticControlUpdateCoordinator.CONFIRMATION_TIMEOUT.inWholeMilliseconds)
        fixture.coordinator.checkTimeout()

        assertEquals(1, fixture.sender.snapshotRequests)
        assertTrue(fixture.coordinator.isResyncing(request.requestId))
        fixture.coordinator.applySnapshot(authority)
        val completion = assertNotNull(fixture.coordinator.takeCompletion(request.requestId))
        assertEquals(ClientAutomaticControlResultKind.NOT_APPLIED, completion.result)
        assertNull(fixture.coordinator.pendingRequest())
        val retry = submitted(fixture, setOf("test:auto_a"))
        assertEquals(4L, retry.expectedRevision)
    }

    /** 請求其實已生效、只是回覆沒有送到時：重新同步看到請求的內容就判定接受，不重送，晚到的回覆也不會再建立完成事件。 */
    @Test
    fun `update applied without a reply is accepted after resync`() {
        val fixture = fixture()
        val authority = snapshot(revision = 4L, supported = setOf("test:auto_a"))
        fixture.coordinator.applySnapshot(authority)
        val request = submitted(fixture, setOf("test:auto_a"))
        fixture.advance(ClientAutomaticControlUpdateCoordinator.CONFIRMATION_TIMEOUT.inWholeMilliseconds)
        fixture.coordinator.checkTimeout()

        val applied = authority.copy(revision = 5L, enabledControlIds = setOf("test:auto_a"))
        fixture.coordinator.applySnapshot(applied)

        val completion = assertNotNull(fixture.coordinator.takeCompletion(request.requestId))
        assertEquals(ClientAutomaticControlResultKind.ACCEPTED, completion.result)
        assertEquals(applied, completion.authoritativeSnapshot)
        assertEquals(listOf(request), fixture.sender.requests)
        assertFalse(fixture.coordinator.applyResult(result(request, AutomaticControlUpdateResultKindDto.ACCEPTED, applied)))
        assertNull(fixture.coordinator.takeCompletion(request.requestId))
    }

    /** 重新同步發現狀態被其他變更取代時判定過期。 */
    @Test
    fun `resync that finds other changes reports stale`() {
        val fixture = fixture()
        val authority = snapshot(revision = 4L, supported = setOf("test:auto_a", "test:auto_b"))
        fixture.coordinator.applySnapshot(authority)
        val request = submitted(fixture, setOf("test:auto_a"))
        fixture.advance(ClientAutomaticControlUpdateCoordinator.CONFIRMATION_TIMEOUT.inWholeMilliseconds)
        fixture.coordinator.checkTimeout()

        fixture.coordinator.applySnapshot(authority.copy(revision = 5L, enabledControlIds = setOf("test:auto_b")))

        assertEquals(ClientAutomaticControlResultKind.STALE, fixture.coordinator.takeCompletion(request.requestId)?.result)
    }

    /** 重新同步期間晚到的較舊快照不結束等待；之後收到的回應以最新權威狀態判斷，完成結果附上同一份狀態。 */
    @Test
    fun `older snapshot during resync is ignored and the response is judged by the latest authority`() {
        val fixture = fixture()
        val authority = snapshot(revision = 4L, supported = setOf("test:auto_a"))
        fixture.coordinator.applySnapshot(authority)
        val request = submitted(fixture, setOf("test:auto_a"))
        val applied = authority.copy(revision = 5L, enabledControlIds = setOf("test:auto_a"))
        fixture.coordinator.applySnapshot(applied)
        fixture.advance(ClientAutomaticControlUpdateCoordinator.CONFIRMATION_TIMEOUT.inWholeMilliseconds)
        fixture.coordinator.checkTimeout()

        fixture.coordinator.applySnapshot(authority)

        assertEquals(request, fixture.coordinator.pendingRequest())
        assertNull(fixture.coordinator.takeCompletion(request.requestId))
        fixture.coordinator.applySnapshot(applied)
        val completion = assertNotNull(fixture.coordinator.takeCompletion(request.requestId))
        assertEquals(ClientAutomaticControlResultKind.ACCEPTED, completion.result)
        assertEquals(applied, completion.authoritativeSnapshot)
    }

    /** 重新同步也沒有回應時，只要求一次並在期限後結束等待，回報無法確認。 */
    @Test
    fun `resync without any snapshot ends the wait as unconfirmed`() {
        val fixture = fixture()
        fixture.coordinator.applySnapshot(snapshot(supported = setOf("test:auto_a")))
        val request = submitted(fixture, setOf("test:auto_a"))

        fixture.advance(ClientAutomaticControlUpdateCoordinator.CONFIRMATION_TIMEOUT.inWholeMilliseconds)
        fixture.coordinator.checkTimeout()
        fixture.advance(ClientAutomaticControlUpdateCoordinator.RESYNC_LIMIT.inWholeMilliseconds - 1)
        fixture.coordinator.checkTimeout()
        assertNotNull(fixture.coordinator.pendingRequest())
        fixture.advance(1)
        fixture.coordinator.checkTimeout()
        fixture.advance(TIMEOUT_AND_RESYNC_MILLIS)
        fixture.coordinator.checkTimeout()

        assertEquals(1, fixture.sender.snapshotRequests)
        assertNull(fixture.coordinator.pendingRequest())
        assertEquals(ClientAutomaticControlResultKind.UNCONFIRMED, fixture.coordinator.takeCompletion(request.requestId)?.result)
    }

    /** 舊請求的回覆比新請求晚到時不影響新請求，新請求的逾時從它自己送出時開始計算。 */
    @Test
    fun `late reply to an earlier request does not affect a newer request`() {
        val fixture = fixture()
        val authority = snapshot(revision = 4L, supported = setOf("test:auto_a"))
        fixture.coordinator.applySnapshot(authority)
        val first = submitted(fixture, setOf("test:auto_a"))
        fixture.advance(ClientAutomaticControlUpdateCoordinator.CONFIRMATION_TIMEOUT.inWholeMilliseconds)
        fixture.coordinator.checkTimeout()
        fixture.coordinator.applySnapshot(authority)
        val second = submitted(fixture, setOf("test:auto_a"))

        assertFalse(fixture.coordinator.applyResult(result(first, AutomaticControlUpdateResultKindDto.REJECTED)))
        fixture.advance(ClientAutomaticControlUpdateCoordinator.CONFIRMATION_TIMEOUT.inWholeMilliseconds - 1)
        fixture.coordinator.checkTimeout()

        assertEquals(second, fixture.coordinator.pendingRequest())
        assertFalse(fixture.coordinator.isResyncing(second.requestId))
        assertEquals(1, fixture.sender.snapshotRequests)
    }

    /**
     * 送出並取回成功送出的請求。
     *
     * @param fixture 使用中的測試 fixture。
     * @param enabled 要啟用的控制項。
     * @return 已送出的請求。
     */
    private fun submitted(fixture: Fixture, enabled: Set<String>): AutomaticControlUpdateRequestDto = assertIs<ClientAutomaticControlSubmitResult.Submitted>(fixture.coordinator.submit(enabled)).request

    /**
     * 建立隔離的 coordinator 與記錄型 sender。
     *
     * @param sendFailure 模擬的同步傳送錯誤；未指定時傳送成功。
     * @return coordinator 與其 sender 的測試 fixture。
     */
    private fun fixture(sendFailure: RuntimeException? = null): Fixture {
        val sender = RecordingSender(sendFailure)
        val clock = MutableClock()
        return Fixture(
            coordinator = ClientAutomaticControlUpdateCoordinator(ClientAutomaticControlStateStore(), sender, clock),
            sender = sender,
            clock = clock,
        )
    }

    /**
     * 建立測試權威快照。
     *
     * @param gameId 快照所屬對局 ID。
     * @param revision 對局內的快照版本。
     * @param supported 伺服器支援的控制項 ID。
     * @param enabled 伺服器已啟用的控制項 ID。
     * @return 指定狀態的權威快照。
     */
    private fun snapshot(
        gameId: String = Uuid.random().toString(),
        revision: Long = 1L,
        supported: Set<String> = emptySet(),
        enabled: Set<String> = emptySet(),
    ): AutomaticControlSnapshotDto = AutomaticControlSnapshotDto(gameId, revision, supported, enabled)

    /**
     * 建立對應 [request] 的權威結果。
     *
     * @param request 被回覆的更新請求。
     * @param kind 回覆結果種類。
     * @param snapshot 回覆攜帶的權威快照，若無則為 `null`。
     * @return 使用請求 ID 與對局 ID 的伺服器結果。
     */
    private fun result(
        request: AutomaticControlUpdateRequestDto,
        kind: AutomaticControlUpdateResultKindDto,
        snapshot: AutomaticControlSnapshotDto? = null,
    ): AutomaticControlUpdateResultDto = AutomaticControlUpdateResultDto(
        requestId = request.requestId,
        gameId = request.gameId,
        result = kind,
        snapshot = snapshot,
    )

    /**
     * 測試所需的協調器與要求記錄器。
     *
     * @property coordinator 受測的本局更新協調器。
     * @property sender 記錄送出要求的替身。
     * @property clock 測試控制的客戶端時間。
     */
    private data class Fixture(
        val coordinator: ClientAutomaticControlUpdateCoordinator,
        val sender: RecordingSender,
        val clock: MutableClock,
    ) {
        /**
         * 讓客戶端時間前進。
         *
         * @param millis 前進的毫秒數。
         */
        fun advance(millis: Long) {
            clock.now += millis
        }
    }

    /** 測試控制的客戶端時間。 */
    private class MutableClock : MonotonicClock {
        /** 目前時間，單位為毫秒。 */
        var now = 0L

        override fun nowMillis(): Long = now
    }

    /**
     * 記錄請求並可模擬同步傳送失敗。
     *
     * @property failure 模擬的同步傳送錯誤；未指定時傳送成功。
     */
    private class RecordingSender(private val failure: RuntimeException?) : ClientAutomaticControlRequestSender {
        /** 已成功交給 sender 的請求。 */
        val requests = mutableListOf<AutomaticControlUpdateRequestDto>()

        /** 要求重新同步的次數。 */
        var snapshotRequests = 0

        /** 記錄 [request]，或拋出測試指定的錯誤。 */
        override fun send(request: AutomaticControlUpdateRequestDto) {
            failure?.let { throw it }
            requests += request
        }

        override fun requestSnapshot() {
            snapshotRequests += 1
        }
    }

    private companion object {
        /** 逾時與重新同步期限加總後再多一段的時間，用來確認之後不會再有任何動作。 */
        val TIMEOUT_AND_RESYNC_MILLIS = (
            ClientAutomaticControlUpdateCoordinator.CONFIRMATION_TIMEOUT + ClientAutomaticControlUpdateCoordinator.RESYNC_LIMIT
            ).inWholeMilliseconds * 2
    }
}
