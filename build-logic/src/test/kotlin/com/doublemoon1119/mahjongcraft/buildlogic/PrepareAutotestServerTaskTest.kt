package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.testfixtures.ProjectBuilder
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證伺服器自動測試開機前準備的執行目錄。 */
class PrepareAutotestServerTaskTest {
    /** 刪掉上次的測試世界、保留其他檔案，寫入固定的伺服器設定與 EULA 同意。 */
    @Test
    fun `prepare resets the world and writes the settings and the EULA`() {
        val root = createTempDirectory("autotest-server").toFile()
        try {
            val runDirectory = root.resolve("run/autotest-server")
            runDirectory.resolve("${PrepareAutotestServerTask.LEVEL_NAME}/level.dat").apply { parentFile.mkdirs() }.writeText("old")
            runDirectory.resolve("autotest/latest.txt").apply { parentFile.mkdirs() }.writeText("kept")
            val project = ProjectBuilder.builder().withProjectDir(root).build()
            val task = project.tasks.register("prepareAutotestServer", PrepareAutotestServerTask::class.java) {
                this.runDirectory.set(runDirectory)
                port.set(25601)
            }.get()

            task.prepare()

            assertFalse(runDirectory.resolve(PrepareAutotestServerTask.LEVEL_NAME).exists(), "The previous test world must be deleted")
            assertTrue(runDirectory.resolve("autotest/latest.txt").exists(), "Earlier reports must stay")
            val properties = runDirectory.resolve("server.properties").readText()
            assertTrue("level-name=${PrepareAutotestServerTask.LEVEL_NAME}" in properties, properties)
            assertTrue("online-mode=false" in properties, properties)
            assertTrue("server-port=25601" in properties, properties)
            assertTrue("eula=true" in runDirectory.resolve("eula.txt").readText())
        } finally {
            root.deleteRecursively()
        }
    }
}
