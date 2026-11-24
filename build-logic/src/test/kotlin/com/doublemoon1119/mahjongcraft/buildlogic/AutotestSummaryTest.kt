package com.doublemoon1119.mahjongcraft.buildlogic

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證總報告如何讀取各目標的報告。 */
class AutotestSummaryTest {
    /** 這次執行期間的報告讀出整體結果、提醒事項與未通過的檢查。 */
    @Test
    fun `a report from this run lists its result, notes and failed checks`() = withRoot { root ->
        writeReport(root, "2026-11-24_15-00-05", status = "failed")

        val entry = AutotestSummary.read("fabric client", root, startedAt = "2026-11-24_15-00-00")

        assertEquals("failed", entry.status)
        assertEquals(listOf("EULA accepted"), entry.notes)
        assertEquals(listOf("table: The room opens - timed out"), entry.failures)
        assertTrue(AutotestSummary.markdown("2026-11-24_15-00-00", listOf(entry)).contains("- [ ] table: The room opens - timed out"))
    }

    /** 比總任務開始還早的報告不算，記為沒有報告。 */
    @Test
    fun `a report from an earlier run counts as missing`() = withRoot { root ->
        writeReport(root, "2026-11-24_14-59-59", status = "passed")

        val entry = AutotestSummary.read("fabric server", root, startedAt = "2026-11-24_15-00-00")

        assertEquals(AutotestSummary.MISSING, entry.status)
    }

    /** 沒有寫出任何報告時記為沒有報告。 */
    @Test
    fun `no report counts as missing`() = withRoot { root ->
        assertEquals(AutotestSummary.MISSING, AutotestSummary.read("forge client", root, startedAt = "2026-11-24_15-00-00").status)
    }

    /**
     * 在報告根資料夾寫一份報告並讓 `latest.txt` 指向它。
     *
     * @param root 報告根資料夾。
     * @param folder 報告資料夾名稱。
     * @param status 報告的整體結果。
     */
    private fun writeReport(root: File, folder: String, status: String) {
        root.resolve(folder).mkdirs()
        root.resolve("latest.txt").writeText(folder)
        root.resolve("$folder/report.json").writeText(
            """
            {
              "target": "minecraft_v1.20.1_fabric",
              "startedAt": "2026-11-24T15:00:05",
              "durationMillis": 1000,
              "status": "$status",
              "notes": ["EULA accepted"],
              "steps": [
                {"id": "world", "status": "passed", "durationMillis": 1, "checks": [{"name": "Entered", "passed": true, "detail": null}],
                  "screenshots": [], "sounds": [], "error": null},
                {"id": "table", "status": "failed", "durationMillis": 1, "checks": [{"name": "The room opens", "passed": false, "detail": "timed out"}],
                  "screenshots": [], "sounds": [], "error": null}
              ]
            }
            """.trimIndent(),
        )
    }

    /**
     * 在暫存的報告根資料夾中執行，結束後刪除。
     *
     * @param body 測試內容。
     */
    private fun withRoot(body: (File) -> Unit) {
        val root = createTempDirectory("autotest-summary").toFile()
        try {
            body(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
