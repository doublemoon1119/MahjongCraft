package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 報告保留規則只刪最舊、名稱符合報告時間格式的資料夾。 */
class AutotestReportRetentionTest {
    /** 每個測試使用的報告根目錄。 */
    private val root: Path = createTempDirectory("autotest-retention")

    /** 刪掉測試建立的檔案。 */
    @AfterTest
    fun cleanUp() {
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }

    /** 超出保留次數時刪掉最舊的報告和裡面的檔案，其他檔案與資料夾不動。 */
    @Test
    fun `oldest report folders beyond the limit are removed`() {
        val reports = (1..5).map { minute -> report("2026-11-24_08-0$minute-00") }
        val notes = Files.createDirectory(root.resolve("notes"))
        val nearMatch = Files.createDirectory(root.resolve("2026-11-24_08-00"))
        val latest = Files.writeString(root.resolve("latest.txt"), reports.last().fileName.toString())
        val removed = AutotestReportRetention.prune(root, keep = 3)
        assertEquals(reports.take(2).toSet(), removed.toSet())
        reports.take(2).forEach { assertTrue(!it.exists()) }
        reports.drop(2).forEach { assertTrue(it.resolve("report.md").exists()) }
        listOf(notes, nearMatch, latest).forEach { assertTrue(it.exists()) }
    }

    /** 沒有超出保留次數或根目錄不存在時不刪任何東西。 */
    @Test
    fun `nothing is removed within the limit`() {
        val reports = (1..2).map { minute -> report("2026-11-24_08-0$minute-00") }
        assertEquals(emptyList(), AutotestReportRetention.prune(root, keep = 3))
        reports.forEach { assertTrue(it.exists()) }
        assertEquals(emptyList(), AutotestReportRetention.prune(root.resolve("missing"), keep = 1))
    }

    /** 保留次數沒有指定時用預設值，不是正整數時拒絕。 */
    @Test
    fun `keep count defaults and rejects invalid values`() {
        assertEquals(AutotestReportRetention.DEFAULT_KEEP, AutotestReportRetention.keepCount(null))
        assertEquals(4, AutotestReportRetention.keepCount("4"))
        listOf("0", "-1", "ten").forEach { value ->
            assertFailsWith<IllegalArgumentException> { AutotestReportRetention.keepCount(value) }
        }
        assertFailsWith<IllegalArgumentException> { AutotestReportRetention.prune(root, keep = 0) }
    }

    /**
     * 建立一個含報告檔案與截圖資料夾的報告資料夾。
     *
     * @param name 資料夾名稱。
     * @return 報告資料夾。
     */
    private fun report(name: String): Path {
        val folder = Files.createDirectory(root.resolve(name))
        Files.writeString(folder.resolve("report.md"), "# Autotest report")
        Files.createDirectory(folder.resolve("screenshots")).also { Files.writeString(it.resolve("shot.png"), "png") }
        return folder
    }
}
