package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/** 驗證自動測試報告轉成 Markdown 的內容。 */
class AutotestReportMarkdownTest {
    /** 報告列出整體結果與各步驟，失敗步驟附上未通過的檢查、截圖、聲音與錯誤；略過的步驟只出現在摘要。 */
    @Test
    fun `markdown lists the results, failed checks, screenshots, sounds and errors`() {
        val report = AutotestReport(
            target = "minecraft_v1.20.1_fabric",
            startedAt = "2026-11-24T10:00",
            durationMillis = 1_500,
            status = AutotestStatus.FAILED,
            steps = listOf(
                AutotestStepResult(
                    id = "world",
                    status = AutotestStatus.FAILED,
                    durationMillis = 1_000,
                    checks = listOf(AutotestCheck("Entered the test world", passed = false, detail = "timed out")),
                    screenshots = listOf("screenshots/world-entered.png"),
                    sounds = listOf(AutotestSoundRecord("minecraft:ui.button.click", 120)),
                    error = "java.lang.IllegalStateException: boom",
                ),
                AutotestStepResult("screens", AutotestStatus.SKIPPED, 0, emptyList(), emptyList(), emptyList(), null),
            ),
        )

        val markdown = autotestReportMarkdown(report)

        assertContains(markdown, "- Result: FAILED")
        assertContains(markdown, "- SKIPPED `screens`")
        assertContains(markdown, "- [ ] Entered the test world - timed out")
        assertContains(markdown, "[screenshots/world-entered.png](screenshots/world-entered.png)")
        assertContains(markdown, "120 ms `minecraft:ui.button.click`")
        assertContains(markdown, "java.lang.IllegalStateException: boom")
        assertFalse(markdown.contains("## screens"))
    }
}
