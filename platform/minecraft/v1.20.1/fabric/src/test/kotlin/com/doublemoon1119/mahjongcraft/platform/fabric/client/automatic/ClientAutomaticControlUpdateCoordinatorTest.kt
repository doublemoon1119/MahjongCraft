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
    @Test
    fun `submit sends a complete replacement based on authoritative snapshot`() {
        val fixture = fixture()
        val snapshot = snapshot(revision = 7L, supported = setOf("test:auto_a", "test:auto_b"))
        fixture.coordinator.applySnapshot(snapshot)

        val submitted = assertIs<ClientAutomaticControlSubmitResult.Submitted>(
            fixture.coordinator.submit(setOf("test:auto_b")),
        )

        assertEquals(snapshot.gameId, submitted.request.gameId)
        assertEquals(7L, submitted.request.expectedRevision)
        assertEquals(setOf("test:auto_b"), submitted.request.enabledControlIds)
        assertEquals(listOf(submitted.request), fixture.sender.requests)
        assertEquals(submitted.request, fixture.coordinator.pendingRequest())
    }

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
            fixture.coordinator.submit(setOf("test:auto_a")),
        )
        assertIs<ClientAutomaticControlSubmitResult.Pending>(fixture.coordinator.submit(emptySet()))
        assertEquals(listOf(submitted.request), fixture.sender.requests)
    }

    @Test
    fun `send failure restores pending state`() {
        val failure = IllegalStateException("network unavailable")
        val fixture = fixture(failure)
        fixture.coordinator.applySnapshot(snapshot(supported = setOf("test:auto_a")))

        val result = assertIs<ClientAutomaticControlSubmitResult.SendFailed>(
            fixture.coordinator.submit(setOf("test:auto_a")),
        )

        assertSame(failure, result.cause)
        assertNull(fixture.coordinator.pendingRequest())
    }

    @Test
    fun `only matching result creates a request scoped completion`() {
        val fixture = fixture()
        fixture.coordinator.applySnapshot(snapshot(revision = 2L, supported = setOf("test:auto_a")))
        val request = assertIs<ClientAutomaticControlSubmitResult.Submitted>(
            fixture.coordinator.submit(setOf("test:auto_a")),
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

    @Test
    fun `clear removes authority pending and completion`() {
        val fixture = fixture()
        fixture.coordinator.applySnapshot(snapshot(supported = setOf("test:auto_a")))
        val request = assertIs<ClientAutomaticControlSubmitResult.Submitted>(
            fixture.coordinator.submit(setOf("test:auto_a")),
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

    /** 建立隔離的 coordinator 與記錄型 sender。 */
    private fun fixture(sendFailure: RuntimeException? = null): Fixture {
        val sender = RecordingSender(sendFailure)
        return Fixture(
            coordinator = ClientAutomaticControlUpdateCoordinator(ClientAutomaticControlStateStore(), sender),
            sender = sender,
        )
    }

    /** 建立測試權威快照。 */
    private fun snapshot(
        gameId: String = Uuid.random().toString(),
        revision: Long = 1L,
        supported: Set<String> = emptySet(),
        enabled: Set<String> = emptySet(),
    ): AutomaticControlSnapshotDto = AutomaticControlSnapshotDto(gameId, revision, supported, enabled)

    /** 建立對應 [request] 的權威結果。 */
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

    /** 測試所需的 coordinator 與 sender。 */
    private data class Fixture(
        /** 被測 coordinator。 */
        val coordinator: ClientAutomaticControlUpdateCoordinator,
        /** 記錄送出請求的 sender。 */
        val sender: RecordingSender,
    )

    /** 記錄請求並可模擬同步傳送失敗。 */
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
