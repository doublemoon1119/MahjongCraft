package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證用戶端自動測試啟動前寫入的遊戲選項。 */
class PrepareAutotestClientTaskTest {
    /** 第一次啟動前沒有選項檔時，寫入啟動時就要生效的選項。 */
    @Test
    fun `prepare writes the startup options into a new run directory`() = withRunDirectory { runDirectory, task ->
        task.prepare()

        assertEquals(listOf("onboardAccessibility:false", "narrator:0"), runDirectory.resolve(PrepareAutotestClientTask.OPTIONS_FILE).readLines())
    }

    /** 已有選項檔時，只改寫啟動時就要生效的選項，其他選項保持原樣。 */
    @Test
    fun `prepare keeps the other options`() = withRunDirectory { runDirectory, task ->
        val options = runDirectory.resolve(PrepareAutotestClientTask.OPTIONS_FILE).apply { parentFile.mkdirs() }
        options.writeText("version:3955\nonboardAccessibility:true\nfov:0.0\nnarrator:3\n")

        task.prepare()

        assertEquals(listOf("version:3955", "fov:0.0", "onboardAccessibility:false", "narrator:0"), options.readLines())
    }

    /**
     * 建立暫存的執行目錄與準備任務，測試後刪除。
     *
     * @param block 使用執行目錄與任務的測試動作。
     */
    private fun withRunDirectory(block: (File, PrepareAutotestClientTask) -> Unit) {
        val root = createTempDirectory("autotest-client").toFile()
        try {
            val runDirectory = root.resolve("run/autotest-client")
            val project = ProjectBuilder.builder().withProjectDir(root).build()
            val task = project.tasks.register("prepareAutotestClient", PrepareAutotestClientTask::class.java) {
                this.runDirectory.set(runDirectory)
            }.get()
            block(runDirectory, task)
        } finally {
            root.deleteRecursively()
        }
    }
}
