package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history.HistoryGenerationFailure
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history.HistoryGenerationProgress
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryDiskUsage
import net.minecraft.util.Formatting
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** 驗證歷史生成 debug 進度訊息只公開安全摘要。 */
class HistoryGenerationChatFormattingTest {
    /** 單則進度訊息帶有一次 mod 前綴，並直接顯示所有批次數字與容量摘要。 */
    @Test
    fun `progress message contains one prefix and visible progress values`() {
        val message = historyGenerationProgressMessage(progress())

        assertTrue(message.string.startsWith("[MahjongCraft]"))
        assertTrue(message.string.contains("7"))
        assertTrue(message.string.contains("4"))
        assertTrue(message.string.contains("2"))
        assertTrue(message.string.contains("1.00 MiB"))
        assertTrue(message.string.contains("Replay"))
        assertTrue(message.string.length < 1600, "Progress output must remain console-readable")
    }

    /** 失敗分類應顯示安全翻譯，不得把原始例外內容帶入訊息。 */
    @Test
    fun `failure progress message hides raw errors and shows localized failure`() {
        val message = historyGenerationProgressMessage(
            progress().copy(
                running = false,
                failure = HistoryGenerationFailure.STORAGE,
                failed = 1,
            ),
        )

        assertTrue(message.string.contains("Storage", ignoreCase = true) || message.string.contains("儲存") || message.string.contains("存储"))
        assertTrue(!message.string.contains("SQLException"))
        assertTrue(!message.string.contains("/world/"))
    }

    /** 每個可見欄位的 bullet 使用灰色，且 fallback 不顯示 raw translation key。 */
    @Test
    fun `progress labels are gray and have readable fallback text`() {
        val message = historyGenerationProgressMessage(progress())
        val bullets = message.siblings.filter { it.string.contains("•") }

        assertTrue(bullets.isNotEmpty())
        assertTrue(bullets.all { it.style.color?.rgb == Formatting.GRAY.colorValue })
        assertTrue(!message.string.contains("mahjongcraft.debug.history."))
    }

    /** 建立固定批次進度資料。 */
    private fun progress(): HistoryGenerationProgress = HistoryGenerationProgress(
        scenarioId = "small",
        requested = 7,
        generated = 4,
        archived = 3,
        pruned = 2,
        pending = 1,
        failed = 0,
        running = true,
        cancelRequested = false,
        elapsed = 12.seconds,
        diskBefore = HistoryDiskUsage(1048576L, 0L, 0L),
        diskAfter = HistoryDiskUsage(2097152L, 0L, 0L),
        replayBytes = 8192L,
        largestReplayBytes = 4096L,
        replaySampleCount = 2,
    )
}
