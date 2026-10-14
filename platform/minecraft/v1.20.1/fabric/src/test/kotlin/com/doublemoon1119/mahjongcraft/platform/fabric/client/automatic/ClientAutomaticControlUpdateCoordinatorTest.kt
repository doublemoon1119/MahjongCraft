package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientAutomaticControlStateStore
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals(AutomaticControlUpdateResultKindDto.ACCEPTED, completion.result.result)
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

    /**
     * 建立隔離的 coordinator 與記錄型 sender。
     *
     * @param sendFailure 模擬的同步傳送錯誤；未指定時傳送成功。
     * @return coordinator 與其 sender 的測試 fixture。
     */
    private fun fixture(sendFailure: RuntimeException? = null): Fixture {
        val sender = RecordingSender(sendFailure)
        return Fixture(
            coordinator = ClientAutomaticControlUpdateCoordinator(ClientAutomaticControlStateStore(), sender),
            sender = sender,
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
     */
    private data class Fixture(
        val coordinator: ClientAutomaticControlUpdateCoordinator,
        val sender: RecordingSender,
    )

    /**
     * 記錄請求並可模擬同步傳送失敗。
     *
     * @property failure 模擬的同步傳送錯誤；未指定時傳送成功。
     */
    private class RecordingSender(private val failure: RuntimeException?) : ClientAutomaticControlRequestSender {
        /** 已成功交給 sender 的請求。 */
        val requests = mutableListOf<AutomaticControlUpdateRequestDto>()

        /** 記錄 [request]，或拋出測試指定的錯誤。 */
        override fun send(request: AutomaticControlUpdateRequestDto) {
            failure?.let { throw it }
            requests += request
        }
    }
}
