package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigUpdateResult
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** [ClientAutoSortHandPreferenceService] 的保存順序與同步語意測試。 */
class ClientAutoSortHandPreferenceServiceTest {
    @Test
    fun `successful change saves locally before sending user change`() = withService { service, sender, store ->
        assertIs<MahjongClientConfigUpdateResult.Success>(store.load())

        assertIs<ClientAutoSortHandPreferenceUpdateResult.Updated>(service.set(false))

        assertFalse(service.current())
        assertEquals(listOf(false), sender.userChanges)
    }

    @Test
    fun `unchanged value does not save or send`() = withService { service, sender, store ->
        assertIs<MahjongClientConfigUpdateResult.Success>(store.load())
        val revision = store.revision

        assertIs<ClientAutoSortHandPreferenceUpdateResult.Unchanged>(service.set(true))

        assertEquals(revision, store.revision)
        assertTrue(sender.userChanges.isEmpty())
    }

    @Test
    fun `save failure does not send user change`() = withService { service, sender, store ->
        assertIs<MahjongClientConfigUpdateResult.Success>(store.load())
        Files.writeString(store.path, Files.readString(store.path) + "\nunknown-field = true\n")

        assertIs<ClientAutoSortHandPreferenceUpdateResult.SaveFailed>(service.set(false))

        assertTrue(service.current())
        assertTrue(sender.userChanges.isEmpty())
    }

    @Test
    fun `send failure keeps locally saved value`() = withService(sendFailure = IllegalStateException("offline")) { service, _, store ->
        assertIs<MahjongClientConfigUpdateResult.Success>(store.load())

        assertIs<ClientAutoSortHandPreferenceUpdateResult.SyncFailed>(service.set(false))

        assertFalse(service.current())
        assertTrue(Files.readString(store.path).contains("auto-sort-hand-enabled = false"))
    }

    @Test
    fun `full draft saves once and retries failed preference sync`() = withService { service, sender, store ->
        assertIs<MahjongClientConfigUpdateResult.Success>(store.load())
        val changed = store.current.copy(tileLabelsEnabled = false, autoSortHandEnabled = false)
        sender.failure = IllegalStateException("temporarily offline")

        assertIs<ClientAutoSortHandPreferenceUpdateResult.SyncFailed>(service.saveDraft(changed, connected = true))
        assertEquals(changed, store.current)
        assertTrue(service.hasPendingSync)
        val savedRevision = store.revision

        sender.failure = null
        assertIs<ClientAutoSortHandPreferenceUpdateResult.Updated>(service.saveDraft(changed, connected = true))
        assertEquals(savedRevision, store.revision)
        assertEquals(listOf(false), sender.userChanges)
        assertFalse(service.hasPendingSync)
    }

    @Test
    fun `full draft does not send preference while disconnected`() = withService { service, sender, store ->
        assertIs<MahjongClientConfigUpdateResult.Success>(store.load())

        assertIs<ClientAutoSortHandPreferenceUpdateResult.Unchanged>(
            service.saveDraft(store.current.copy(autoSortHandEnabled = false), connected = false),
        )

        assertFalse(service.current())
        assertTrue(sender.userChanges.isEmpty())
    }

    @Test
    fun `restore sends current value without modifying config`() = withService { service, sender, store ->
        assertIs<MahjongClientConfigUpdateResult.Success>(store.load())
        val revision = store.revision

        service.restoreToServer()

        assertEquals(listOf(true), sender.restores)
        assertEquals(revision, store.revision)
    }

    /** 建立隔離設定檔與記錄型 sender，並在測試後移除暫存目錄。 */
    private fun withService(
        sendFailure: RuntimeException? = null,
        block: (ClientAutoSortHandPreferenceService, RecordingSender, MahjongClientConfigStore) -> Unit,
    ) {
        val directory = Files.createTempDirectory("mahjongcraft-auto-sort-test")
        try {
            val store = MahjongClientConfigStore.createForTesting(directory.resolve("client.toml"))
            val sender = RecordingSender(sendFailure)
            block(ClientAutoSortHandPreferenceService(store, sender), sender, store)
        } finally {
            deleteRecursively(directory)
        }
    }

    /** 刪除測試暫存目錄。 */
    private fun deleteRecursively(path: Path) {
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    /** 記錄兩種偏好同步語意。 */
    private class RecordingSender(var failure: RuntimeException?) : ClientAutoSortHandPreferenceSender {
        /** 玩家主動變更紀錄。 */
        val userChanges = mutableListOf<Boolean>()

        /** 加入世界恢復紀錄。 */
        val restores = mutableListOf<Boolean>()

        /** 記錄 USER_CHANGE，或拋出指定錯誤。 */
        override fun sendUserChange(enabled: Boolean) {
            failure?.let { throw it }
            userChanges += enabled
        }

        /** 記錄 RESTORE。 */
        override fun sendRestore(enabled: Boolean) {
            restores += enabled
        }
    }
}
