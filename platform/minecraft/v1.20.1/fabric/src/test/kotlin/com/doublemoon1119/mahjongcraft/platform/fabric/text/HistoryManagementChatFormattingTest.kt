package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryCleanupPreview
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryCleanupReason
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryCleanupReport
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryDiskUsage
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryRetentionPolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryStorageSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftConfigCommandKeys
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import net.minecraft.util.Formatting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** 驗證歷史管理指令的可見數字、分類懸停與安全容量格式。 */
class HistoryManagementChatFormattingTest {
    /** 用量訊息直接顯示四類場數、磁碟摘要、更新時間與過期標記。 */
    @Test
    fun `storage message exposes visible counts disk time and stale marker`() {
        val message = historyStorageMessage(snapshot(), stale = true)

        assertTrue(message.string.contains("3"))
        assertTrue(message.string.contains("2"))
        assertTrue(message.string.contains("1.00 MiB"))
        assertTrue(message.string.contains("1048576 bytes"))
        assertTrue(message.string.contains("2026-01-01T00:00:00Z"))
        assertTrue(message.string.contains("stale", ignoreCase = true))
        assertEquals(4, message.siblings.count { it.style.hoverEvent != null })
        assertTrue(message.string.length < 1000, "Visible storage output must remain concise")
    }

    /** 四個場次分類各自擁有短懸停內容，不把所有欄位集中到單一提示。 */
    @Test
    fun `storage message has four independent category hovers`() {
        val message = historyStorageMessage(snapshot())
        val labels = message.siblings.filter { it.style.hoverEvent != null }

        assertEquals(4, labels.size)
        labels.forEach { label ->
            val hover = assertNotNull(label.style.hoverEvent?.getValue(HoverEvent.Action.SHOW_TEXT))
            assertTrue(hover.string.length < 500, "Each category hover must remain short")
        }
    }

    /** 清理預覽顯示候選場數與邏輯資料量，不誤稱為已移除場次或確定回收空間。 */
    @Test
    fun `cleanup preview distinguishes candidates from freed disk`() {
        val preview = HistoryCleanupPreview(
            countsByReason = mapOf(HistoryCleanupReason.EXPIRED to 2L),
            logicalBytes = 4096L,
            disk = HistoryDiskUsage(1024L, 2048L, 1024L),
            additionalCandidateCount = 3L,
            additionalLogicalBytes = 8192L,
            policy = policy(),
            evaluatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        val message = historyCleanupPreviewMessage(preview)

        assertTrue(message.string.contains("2"))
        assertTrue(message.string.contains("4.00 KiB") || message.string.contains("4096 bytes"))
        assertTrue(message.string.contains("3"))
        assertTrue(message.string.contains("logical", ignoreCase = true) || message.string.contains("候選"))
        assertTrue(message.string.length < 1000, "Visible cleanup preview must remain concise")
        assertCleanupLabelColors(
            message,
            setOf(
                MinecraftConfigCommandKeys.HISTORY_CANDIDATE_MATCHES,
                MinecraftConfigCommandKeys.HISTORY_LOGICAL_BYTES,
                MinecraftConfigCommandKeys.HISTORY_ADDITIONAL_CANDIDATES,
            ),
        )
        assertEquals(Formatting.GREEN.colorValue, message.siblings.first { it.string == "2" }.style.color?.rgb)
        assertEquals(Formatting.YELLOW.colorValue, message.siblings.first { it.string == "3" }.style.color?.rgb)
    }

    /** 清理報告保留部分完成狀態，並顯示正的 signed 磁碟變化。 */
    @Test
    fun `cleanup report preserves partial state and signed disk growth`() {
        val report = HistoryCleanupReport(
            removedMatches = 2L,
            diskBefore = HistoryDiskUsage(1024L, 0L, 0L),
            diskAfter = HistoryDiskUsage(2048L, 0L, 0L),
            storageAvailable = false,
            recoveryBusy = true,
            incrementalSupported = false,
            completed = false,
        )

        val message = historyCleanupReportMessage(report)

        assertTrue(message.string.contains("+"))
        assertTrue(message.string.contains("partial", ignoreCase = true) || message.string.contains("部分"))
        assertTrue(message.string.contains("2"))
        assertTrue(message.string.length < 800, "Visible cleanup result must remain concise")
        assertCleanupLabelColors(
            message,
            setOf(
                MinecraftConfigCommandKeys.HISTORY_REMOVED_MATCHES,
                MinecraftConfigCommandKeys.HISTORY_NET_DISK_CHANGE,
                MinecraftConfigCommandKeys.HISTORY_RESULT_STATE,
            ),
        )
        assertEquals(Formatting.GREEN.colorValue, message.siblings.first { it.string == "2" }.style.color?.rgb)
    }

    /**
     * 驗證條列名稱與符號使用灰色，而 mod 前綴仍使用金色。
     *
     * @param message 受測的 preview 或 run 訊息。
     * @param keys 預期出現的條列項目名稱。
     */
    private fun assertCleanupLabelColors(message: Text, keys: Set<String>) {
        assertEquals(Formatting.GOLD.colorValue, message.style.color?.rgb)
        val labels = message.siblings.filter { (it.content as? TranslatableTextContent)?.key in keys }
        assertEquals(keys.size, labels.size)
        labels.forEach { assertEquals(Formatting.GRAY.colorValue, it.style.color?.rgb) }
        val bullets = message.siblings.filter { it.string == "\n  • " }
        assertEquals(keys.size, bullets.size)
        bullets.forEach { assertEquals(Formatting.GRAY.colorValue, it.style.color?.rgb) }
    }

    /** 建立具有固定磁碟與政策值的測試快照。 */
    private fun snapshot(): HistoryStorageSnapshot = HistoryStorageSnapshot(
        completedMatchCount = 3L,
        activeMatchCount = 2L,
        partialMatchCount = 1L,
        unknownMatchCount = 0L,
        pendingSqlEventCount = 4L,
        pendingOutboxEventCount = 5L,
        tombstoneCount = 6L,
        disk = HistoryDiskUsage(1048576L, 0L, 0L),
        policy = policy(),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    /** 建立不限制場數與天數的固定保留政策。 */
    private fun policy(): HistoryRetentionPolicy = HistoryRetentionPolicy(
        includeInterruptedMatches = false,
        maxMatches = 0,
        retentionDuration = 0.days,
        maxDiskBytes = 16L * 1024 * 1024,
    )
}
