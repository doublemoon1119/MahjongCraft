package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigUpdateResult
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientAutomaticControlStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.BuiltInMinecraftAutomaticControlIds
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [ClientAutomaticControlCommandService] 的控制路由、集合替換與 ACK 配對測試。 */
class ClientAutomaticControlCommandServiceTest {
    @Test
    fun `available controls combine permanent preference and authoritative round controls`() = withFixture { fixture ->
        fixture.coordinator.applySnapshot(snapshot(supported = setOf("test:auto_win", "test:no_calls")))

        assertEquals(
            setOf(BuiltInMinecraftAutomaticControlIds.AUTO_SORT_HAND, "test:auto_win", "test:no_calls"),
            fixture.service.availableControlIds(),
        )
    }

    @Test
    fun `round control update preserves every other confirmed control`() = withFixture { fixture ->
        fixture.coordinator.applySnapshot(
            snapshot(
                revision = 4L,
                supported = setOf("test:auto_win", "test:no_calls", "test:auto_discard"),
                enabled = setOf("test:auto_win", "test:auto_discard"),
            ),
        )

        val submitted = assertIs<ClientAutomaticControlCommandResult.Submitted>(
            fixture.service.execute("test:no_calls", AutomaticControlCommandMode.ON),
        )

        assertTrue(submitted.enabled)
        assertFalse(submitted.previousEnabled)
        assertEquals(
            setOf("test:auto_win", "test:no_calls", "test:auto_discard"),
            fixture.roundSender.requests.single().enabledControlIds,
        )
    }

    @Test
    fun `off and toggle use confirmed state without optimistic mutation`() = withFixture { fixture ->
        fixture.coordinator.applySnapshot(
            snapshot(
                supported = setOf("test:auto_win", "test:no_calls"),
                enabled = setOf("test:auto_win", "test:no_calls"),
            ),
        )

        assertIs<ClientAutomaticControlCommandResult.Unchanged>(
            fixture.service.execute("test:auto_win", AutomaticControlCommandMode.ON),
        )
        val submitted = assertIs<ClientAutomaticControlCommandResult.Submitted>(
            fixture.service.execute("test:no_calls", AutomaticControlCommandMode.TOGGLE),
        )

        assertFalse(submitted.enabled)
        assertTrue(submitted.previousEnabled)
        assertEquals(setOf("test:auto_win"), fixture.roundSender.requests.single().enabledControlIds)
        assertTrue("test:no_calls" in fixture.coordinator.snapshot()!!.enabledControlIds)
    }

    @Test
    fun `round control reports unavailable unsupported pending and send failure`() = withFixture { fixture ->
        assertIs<ClientAutomaticControlCommandResult.Unavailable>(
            fixture.service.execute("test:auto_win", AutomaticControlCommandMode.ON),
        )
        fixture.coordinator.applySnapshot(snapshot(supported = setOf("test:auto_win")))
        assertIs<ClientAutomaticControlCommandResult.Unsupported>(
            fixture.service.execute("test:unknown", AutomaticControlCommandMode.ON),
        )
        assertIs<ClientAutomaticControlCommandResult.Submitted>(
            fixture.service.execute("test:auto_win", AutomaticControlCommandMode.ON),
        )
        assertIs<ClientAutomaticControlCommandResult.Pending>(
            fixture.service.execute("test:auto_win", AutomaticControlCommandMode.ON),
        )
    }

    @Test
    fun `round control send failure clears pending request`() = withFixture(
        roundFailure = IllegalStateException("offline"),
    ) { fixture ->
        fixture.coordinator.applySnapshot(snapshot(supported = setOf("test:auto_win")))

        assertIs<ClientAutomaticControlCommandResult.RoundControlSendFailed>(
            fixture.service.execute("test:auto_win", AutomaticControlCommandMode.ON),
        )

        assertNull(fixture.coordinator.pendingRequest())
    }

    @Test
    fun `completion is request scoped and exposes authoritative final state`() = withFixture { fixture ->
        fixture.coordinator.applySnapshot(snapshot(supported = setOf("test:auto_win")))
        val submitted = assertIs<ClientAutomaticControlCommandResult.Submitted>(
            fixture.service.execute("test:auto_win", AutomaticControlCommandMode.ON),
        )
        val acceptedSnapshot = snapshot(
            gameId = fixture.coordinator.snapshot()!!.gameId,
            revision = 2L,
            supported = setOf("test:auto_win"),
            enabled = setOf("test:auto_win"),
        )
        fixture.coordinator.applyResult(
            AutomaticControlUpdateResultDto(
                requestId = submitted.requestId,
                gameId = acceptedSnapshot.gameId,
                result = AutomaticControlUpdateResultKindDto.ACCEPTED,
                snapshot = acceptedSnapshot,
            ),
        )

        assertNull(fixture.service.takeCompletion("another-request", "test:auto_win"))
        val completion = fixture.service.takeCompletion(submitted.requestId, "test:auto_win")!!
        assertEquals(AutomaticControlUpdateResultKindDto.ACCEPTED, completion.result)
        assertEquals(true, completion.enabled)
        assertNull(fixture.service.takeCompletion(submitted.requestId, "test:auto_win"))
    }

    @Test
    fun `auto sort command delegates to persistent preference service`() = withFixture { fixture ->
        val controlId = BuiltInMinecraftAutomaticControlIds.AUTO_SORT_HAND

        val updated = assertIs<ClientAutomaticControlCommandResult.Updated>(
            fixture.service.execute(controlId, AutomaticControlCommandMode.OFF),
        )

        assertFalse(updated.enabled)
        assertTrue(updated.previousEnabled)
        assertEquals(listOf(false), fixture.preferenceSender.userChanges)
        assertFalse(fixture.preferenceService.current())
    }

    /** 建立隔離的設定檔、權威 store 與記錄型 sender。 */
    private fun withFixture(
        roundFailure: RuntimeException? = null,
        block: (Fixture) -> Unit,
    ) {
        val directory = Files.createTempDirectory("mahjongcraft-automatic-command-test")
        try {
            val configStore = MahjongClientConfigStore.createForTesting(directory.resolve("client.toml"))
            assertIs<MahjongClientConfigUpdateResult.Success>(configStore.load())
            val preferenceSender = RecordingPreferenceSender()
            val preferenceService = ClientAutoSortHandPreferenceService(configStore, preferenceSender)
            val roundSender = RecordingRoundSender(roundFailure)
            val coordinator = ClientAutomaticControlUpdateCoordinator(ClientAutomaticControlStateStore(), roundSender)
            block(
                Fixture(
                    service = ClientAutomaticControlCommandService(preferenceService, coordinator),
                    preferenceService = preferenceService,
                    preferenceSender = preferenceSender,
                    coordinator = coordinator,
                    roundSender = roundSender,
                ),
            )
        } finally {
            deleteRecursively(directory)
        }
    }

    /** 建立權威本局快照。 */
    private fun snapshot(
        gameId: String = "00000000-0000-0000-0000-000000000001",
        revision: Long = 1L,
        supported: Set<String> = emptySet(),
        enabled: Set<String> = emptySet(),
    ): AutomaticControlSnapshotDto = AutomaticControlSnapshotDto(gameId, revision, supported, enabled)

    /** 刪除測試暫存目錄。 */
    private fun deleteRecursively(path: Path) {
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    /** 測試所需服務與記錄器。 */
    private data class Fixture(
        /** 被測指令服務。 */
        val service: ClientAutomaticControlCommandService,
        /** 永久偏好服務。 */
        val preferenceService: ClientAutoSortHandPreferenceService,
        /** 永久偏好 sender。 */
        val preferenceSender: RecordingPreferenceSender,
        /** 本局更新協調器。 */
        val coordinator: ClientAutomaticControlUpdateCoordinator,
        /** 本局 request sender。 */
        val roundSender: RecordingRoundSender,
    )

    /** 記錄永久偏好的 USER_CHANGE 與 RESTORE。 */
    private class RecordingPreferenceSender : ClientAutoSortHandPreferenceSender {
        /** USER_CHANGE 紀錄。 */
        val userChanges = mutableListOf<Boolean>()

        /** 記錄玩家主動變更。 */
        override fun sendUserChange(enabled: Boolean) {
            userChanges += enabled
        }

        /** 本測試不需保存 RESTORE 紀錄。 */
        override fun sendRestore(enabled: Boolean) = Unit
    }

    /** 記錄本局完整替換 request，並可模擬傳送失敗。 */
    private class RecordingRoundSender(private val failure: RuntimeException?) : ClientAutomaticControlRequestSender {
        /** 成功交給 sender 的 requests。 */
        val requests = mutableListOf<AutomaticControlUpdateRequestDto>()

        /** 記錄 [request] 或拋出指定錯誤。 */
        override fun send(request: AutomaticControlUpdateRequestDto) {
            failure?.let { throw it }
            requests += request
        }
    }
}
