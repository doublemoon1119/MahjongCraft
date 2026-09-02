package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 驗證 client config 的註解保留、原子套用與失敗保護。 */
class MahjongClientConfigStoreTest {
    /** 缺少檔案時應建立正式模板並載入預設值。 */
    @Test
    fun `load creates the annotated default template`() = withTemporaryConfig { store, path ->
        val result = assertIs<MahjongClientConfigUpdateResult.Success>(store.load())

        assertTrue(result.createdDefaultFile)
        assertEquals(MahjongClientConfigState(), result.config)
        assertTrue(Files.readString(path).contains("# MahjongCraft client configuration."))
        assertEquals(1L, store.revision)
    }

    /** 保存完整草稿時應保留既有註解並可重新載入。 */
    @Test
    fun `save preserves comments and applies the complete draft`() = withTemporaryConfig { store, path ->
        assertIs<MahjongClientConfigUpdateResult.Success>(store.load())
        Files.writeString(
            path,
            Files.readString(path)
                .replace("tile-labels-enabled = false", "tile-labels-enabled   =   false # Inline note") +
                "\n# User note\n",
        )
        val requested = MahjongClientConfigState(tileLabelsEnabled = true, autoSortHandEnabled = false)

        assertIs<MahjongClientConfigUpdateResult.Success>(store.save(requested))

        val content = Files.readString(path)
        assertTrue(content.contains("# User note"))
        assertTrue(content.contains("tile-labels-enabled   =   true # Inline note"))
        assertTrue(content.contains("auto-sort-hand-enabled = false"))
        assertEquals(requested, store.current)
        assertEquals(2L, store.revision)
        assertEquals(requested, assertIs<MahjongClientConfigUpdateResult.Success>(store.load()).config)
    }

    /** 無法解碼更新後內容時不得覆寫磁碟或 runtime 設定。 */
    @Test
    fun `save failure preserves the previous file and runtime state`() = withTemporaryConfig { store, path ->
        assertIs<MahjongClientConfigUpdateResult.Success>(store.load())
        val previous = store.current
        val invalid = Files.readString(path) + "\nunknown-field = true\n"
        Files.writeString(path, invalid)

        assertIs<MahjongClientConfigUpdateResult.Failure>(
            store.save(previous.copy(tileLabelsEnabled = !previous.tileLabelsEnabled)),
        )

        assertEquals(invalid, Files.readString(path))
        assertEquals(previous, store.current)
        assertEquals(1L, store.revision)
    }

    /** 缺少受控欄位時應明確失敗，不得將欄位附加到未知 TOML section。 */
    @Test
    fun `save rejects a missing controlled field without changing the file`() = withTemporaryConfig { store, path ->
        assertIs<MahjongClientConfigUpdateResult.Success>(store.load())
        val missingField = Files.readString(path).lineSequence()
            .filterNot { it.trimStart().startsWith("tile-labels-enabled") }
            .joinToString("\n")
        Files.writeString(path, missingField)

        val result = assertIs<MahjongClientConfigUpdateResult.Failure>(
            store.save(store.current.copy(tileLabelsEnabled = true)),
        )

        assertTrue(result.message.contains("tile-labels-enabled"))
        assertEquals(missingField, Files.readString(path))
        assertEquals(MahjongClientConfigState(), store.current)
        assertEquals(1L, store.revision)
    }

    /** 建立隔離設定路徑並於測試結束後清理。 */
    private fun withTemporaryConfig(block: (MahjongClientConfigStore, Path) -> Unit) {
        val directory = Files.createTempDirectory("mahjongcraft-client-config-")
        try {
            val path = directory.resolve("nested/client.toml")
            block(MahjongClientConfigStore.createForTesting(path), path)
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
