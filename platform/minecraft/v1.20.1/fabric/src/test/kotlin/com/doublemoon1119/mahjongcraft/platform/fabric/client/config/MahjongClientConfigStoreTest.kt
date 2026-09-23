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
        assertEquals(0.95, result.config.hudLayout.compactPromptX)
        assertEquals(0.78, result.config.hudLayout.compactPromptY)
        assertTrue(Files.readString(path).contains("# MahjongCraft client configuration."))
        assertTrue(Files.readString(path).contains("compact-prompt-x = 0.95"))
        assertTrue(Files.readString(path).contains("compact-prompt-y = 0.78"))
        assertTrue(Files.readString(path).contains("discard-analysis-y = 0.8"))
        assertTrue(Files.readString(path).contains("automatic-control-status-enabled = true"))
        assertTrue(Files.readString(path).contains("automatic-control-status-x = 0.03"))
        assertTrue(Files.readString(path).contains("automatic-control-status-y = 0.22"))
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
        val requested = MahjongClientConfigState(
            tileLabelsEnabled = true,
            autoSortHandEnabled = false,
            hudLayout = MahjongHudLayoutConfig(
                decisionPanelY = 0.25,
                compactPromptX = 0.75,
                compactPromptY = 0.4,
                discardAnalysisY = 0.6,
            ),
            presentationVisibility = MahjongPresentationVisibilityConfig(
                roundInfoEnabled = false,
                discardAnalysisEnabled = false,
                matchingTileHighlightEnabled = false,
            ),
        )

        assertIs<MahjongClientConfigUpdateResult.Success>(store.save(requested))

        val content = Files.readString(path)
        assertTrue(content.contains("# User note"))
        assertTrue(content.contains("tile-labels-enabled   =   true # Inline note"))
        assertTrue(content.contains("auto-sort-hand-enabled = false"))
        assertTrue(content.contains("decision-panel-y = 0.25"))
        assertTrue(content.contains("compact-prompt-x = 0.75"))
        assertTrue(content.contains("round-info-enabled = false"))
        assertTrue(content.contains("matching-tile-highlight-enabled = false"))
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

    /** 舊版設定缺少整個 HUD section 時，首次保存應保留舊內容並附加預設結構。 */
    @Test
    fun `save upgrades a legacy config without hud layout fields`() = withTemporaryConfig { store, path ->
        assertIs<MahjongClientConfigUpdateResult.Success>(store.load())
        val legacy = Files.readString(path).substringBefore("\n# HUD positions") + "\n# Legacy note\n"
        Files.writeString(path, legacy)
        assertIs<MahjongClientConfigUpdateResult.Success>(store.load())

        val requested = store.current.copy(tileLabelsEnabled = true)
        assertIs<MahjongClientConfigUpdateResult.Success>(store.save(requested))

        val updated = Files.readString(path)
        assertTrue(updated.contains("# Legacy note"))
        assertTrue(updated.contains("[hud-layout]"))
        assertEquals(requested, store.current)
    }

    /** 完整舊版 section 應只補入新增欄位，並保留原有註解與欄位內容。 */
    @Test
    fun `save upgrades complete legacy sections idempotently`() = withTemporaryConfig { store, path ->
        assertIs<MahjongClientConfigUpdateResult.Success>(store.load())
        val current = Files.readString(path)
        val legacy = current
            .lineSequence()
            .filterNot { it.contains("automatic-control-status-enabled") }
            .filterNot { it.contains("automatic-control-status-x") || it.contains("automatic-control-status-y") }
            .joinToString("\n")
            .replace("[presentation-visibility]", "# Legacy visibility note\n[presentation-visibility]")
        Files.writeString(path, legacy)

        assertIs<MahjongClientConfigUpdateResult.Success>(store.load())
        assertIs<MahjongClientConfigUpdateResult.Success>(store.save(store.current))
        val updated = Files.readString(path)
        assertTrue(updated.contains("# Legacy visibility note"))
        assertEquals(1, Regex("automatic-control-status-x\\s*=").findAll(updated).count())
        assertEquals(1, Regex("automatic-control-status-y\\s*=").findAll(updated).count())
        assertEquals(1, Regex("automatic-control-status-enabled\\s*=").findAll(updated).count())

        val afterFirstSave = updated
        assertIs<MahjongClientConfigUpdateResult.Success>(store.save(store.current))
        assertEquals(afterFirstSave, Files.readString(path))
    }

    /** 新版受控 section 只缺一個欄位時，應拒絕猜測而不改寫檔案。 */
    @Test
    fun `save rejects partial additions to controlled sections`() = withTemporaryConfig { store, path ->
        assertIs<MahjongClientConfigUpdateResult.Success>(store.load())
        val partial = Files.readString(path).lineSequence()
            .filterNot { it.trimStart().startsWith("automatic-control-status-x") }
            .joinToString("\n")
        Files.writeString(path, partial)

        val result = assertIs<MahjongClientConfigUpdateResult.Failure>(store.save(store.current))

        assertTrue(result.message.contains("hud-layout"))
        assertEquals(partial, Files.readString(path))
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
