package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 驗證根專案的總任務 `autotestServer`。
 *
 * - 不需要任何開關，只執行並判定伺服器自動測試。
 * - `autotest` 仍要開關，並涵蓋用戶端與伺服器。
 */
class AutotestServerAggregateTest {
    /** 伺服器報告通過時總任務成功，不需要開關，而且不啟動用戶端與平常的開發伺服器。 */
    @Test
    fun `server aggregate runs only the server test without a switch`() = withFixture(withLoader = true) { root ->
        val result = runner(root, "autotestServer", "-PfakeServerStatus=passed").build()

        assertTrue(result.ran(":minecraft_test_fabric:runAutotestServerStart"), result.output)
        assertTrue(result.ran(":minecraft_test_fabric:runAutotestServerResume"), result.output)
        assertFalse(result.ran(":minecraft_test_fabric:runClient"), result.output)
        assertFalse(result.ran(":minecraft_test_fabric:runServer"), result.output)
        assertTrue(summary(root).contains("- PASSED minecraft_test_fabric server"), summary(root))
    }

    /** 總報告資料夾收有伺服器的報告與這次執行寫出的紀錄檔，上傳一個資料夾就包含全部。 */
    @Test
    fun `server aggregate collects the report and the logs beside the summary`() = withFixture(withLoader = true) { root ->
        runner(root, "autotestServer", "-PfakeServerStatus=failed").buildAndFail()

        val collected = summaryFile(root).parentFile.resolve("minecraft_test_fabric-server")
        assertTrue(collected.resolve("report.json").isFile, "The server report was not collected")
        assertTrue(collected.resolve("logs/latest.log").isFile, "The server log was not collected")
    }

    /** 伺服器報告未通過時總任務失敗。 */
    @Test
    fun `server aggregate fails when the server report did not pass`() = withFixture(withLoader = true) { root ->
        val result = runner(root, "autotestServer", "-PfakeServerStatus=failed").buildAndFail()

        assertTrue(result.output.contains("Automated tests did not pass: minecraft_test_fabric server failed"), result.output)
    }

    /** 伺服器沒有寫出報告時總任務失敗。 */
    @Test
    fun `server aggregate fails when the server wrote no report`() = withFixture(withLoader = true) { root ->
        val result = runner(root, "autotestServer").buildAndFail()

        assertTrue(result.output.contains("Automated tests did not pass: minecraft_test_fabric server missing"), result.output)
    }

    /** 所選目標沒有伺服器自動測試時，總任務成功並寫明沒有要執行的測試。 */
    @Test
    fun `server aggregate passes when no module has a server test`() = withFixture(withLoader = false) { root ->
        runner(root, "autotestServer").build()

        assertTrue(summary(root).contains(AutotestSummary.NOTHING_TO_RUN), summary(root))
    }

    /** 沒有開啟自動測試時不登記總任務 `autotest`。 */
    @Test
    fun `full aggregate is not registered without the switch`() = withFixture(withLoader = true) { root ->
        val rootTasks = runner(root, "tasks", "--all").build().output.lines()

        assertFalse(rootTasks.any { it.startsWith("autotest ") }, rootTasks.joinToString("\n"))
        assertTrue(rootTasks.any { it.startsWith("autotestServer ") }, rootTasks.joinToString("\n"))
    }

    /** `autotest` 執行用戶端與伺服器，並彙整兩者的報告。 */
    @Test
    fun `full aggregate still runs and summarizes both sides`() = withFixture(withLoader = true) { root ->
        val result = runner(root, "autotest", "-PmahjongcraftAutotest=true", "-PfakeServerStatus=passed", "-PfakeClientStatus=passed").build()

        assertTrue(result.ran(":minecraft_test_fabric:runClient"), result.output)
        assertTrue(result.ran(":minecraft_test_fabric:runAutotestServerStart"), result.output)
        val summary = summary(root, folder = "autotest")
        assertTrue(summary.contains("- PASSED minecraft_test_fabric client"), summary)
        assertTrue(summary.contains("- PASSED minecraft_test_fabric server"), summary)
    }

    /**
     * 這次建置是否執行了某個任務。
     *
     * @receiver 建置結果。
     * @param path 任務路徑。
     * @return 執行過時為 true。
     */
    private fun BuildResult.ran(path: String): Boolean = task(path) != null

    /**
     * 讀取總任務寫出的總報告。
     *
     * @param root fixture 根目錄。
     * @param folder 總報告的根資料夾名稱。
     * @return 總報告的內容。
     */
    private fun summary(root: File, folder: String = "autotest-server"): String = summaryFile(root, folder).readText()

    /**
     * 總任務寫出的總報告檔案。
     *
     * @param root fixture 根目錄。
     * @param folder 總報告的根資料夾名稱。
     * @return 總報告檔案。
     */
    private fun summaryFile(root: File, folder: String = "autotest-server"): File =
        root.resolve("build/reports/$folder").walkTopDown().single { it.name == "summary.md" }

    /**
     * 建立 fixture。載入器模組以假的遊戲執行任務代替真正的遊戲，依 Gradle 屬性寫出報告：
     *
     * - `fakeServerStatus`：`runAutotestServerStart` 寫出這個結果的伺服器報告，沒有指定時不寫報告。
     * - 這兩個任務都在執行目錄的 `logs` 寫一份紀錄檔。
     * - `fakeClientStatus`：`runClient` 寫出這個結果的用戶端報告，沒有指定時不寫報告。
     *
     * @param root fixture 根目錄。
     * @param withLoader 是否加入有用戶端與伺服器自動測試的載入器模組。
     */
    private fun prepareFixture(root: File, withLoader: Boolean) {
        root.resolve("gradle/libs.versions.toml").apply { parentFile.mkdirs() }.writeText(
            """
            [versions]
            core-java-toolchain = "17"
            core-java-release = "17"
            kotlin-api-version = "2.4"
            """.trimIndent() + "\n",
        )
        root.resolve("docs/assets/branding/logo.png").apply { parentFile.mkdirs() }.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47))
        root.resolve("settings.gradle").writeText(
            buildString {
                appendLine("rootProject.name = \"autotest-server-aggregate-fixture\"")
                if (withLoader) {
                    appendLine("include(\":minecraft_test_fabric\")")
                    appendLine("project(\":minecraft_test_fabric\").projectDir = file(\"platform/v1.20.1/fabric\")")
                    appendLine("gradle.ext.set(\"mahjongcraft.targetJavaToolchain\", [\":minecraft_test_fabric\": 17])")
                    appendLine("gradle.ext.set(\"mahjongcraft.targetJavaRelease\", [\":minecraft_test_fabric\": 17])")
                    appendLine("gradle.ext.set(\"mahjongcraft.targetIndex\", [\":minecraft_test_fabric\": 0])")
                }
            },
        )
        root.resolve("build.gradle").writeText(
            """
            plugins {
                // 只為了讓根專案的建置腳本讀得到 build-logic 的類別，不套用到根專案。
                id("mahjongcraft.minecraft-loader") apply false
            }

            version = "0.0.0-dev"
            ext.projectId = "mahjongcraft"
            ext.projectDisplayName = "MahjongCraft"
            com.doublemoon1119.mahjongcraft.buildlogic.AutotestAggregationKt.registerAutotestAggregate(project)
            """.trimIndent() + "\n",
        )
        if (!withLoader) return
        root.resolve("platform/v1.20.1/fabric/build.gradle").apply { parentFile.mkdirs() }.writeText(
            """
            plugins {
                id("mahjongcraft.minecraft-loader")
            }

            def fakeRun = { String name, String side, String statusProperty ->
                def status = providers.gradleProperty(statusProperty).orNull
                def reports = layout.projectDirectory.dir("run/autotest-" + side + "/autotest").asFile
                def logs = layout.projectDirectory.dir("run/autotest-" + side + "/logs").asFile
                tasks.register(name) {
                    doLast {
                        logs.mkdirs()
                        new File(logs, "latest.log").text = "started"
                        if (status != null) {
                            def folder = new File(reports, "$REPORT_FOLDER")
                            folder.mkdirs()
                            new File(reports, "latest.txt").text = "$REPORT_FOLDER"
                            new File(folder, "report.json").text = '{"status": "' + status + '", "notes": [], "steps": []}'
                        }
                    }
                }
            }
            fakeRun("runClient", "client", "fakeClientStatus")
            fakeRun("runAutotestServerStart", "server", "fakeServerStatus")
            tasks.register("runAutotestServerResume")
            tasks.register("runServer")

            com.doublemoon1119.mahjongcraft.buildlogic.AutotestRunSupportKt.registerAutotestClientTasks(project, "runClient")
            com.doublemoon1119.mahjongcraft.buildlogic.AutotestRunSupportKt.registerAutotestServerTasks(project, "runAutotestServerStart", "runAutotestServerResume")
            """.trimIndent() + "\n",
        )
    }

    /**
     * 建立執行 fixture 的 Gradle runner。
     *
     * @param root fixture 根目錄。
     * @param arguments 任務與 Gradle 屬性。
     * @return runner。
     */
    private fun runner(root: File, vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(root)
        .withPluginClasspath()
        .withArguments(*arguments, "--stacktrace")
        .forwardOutput()

    /**
     * 建立並清理一次性的 fixture 專案。
     *
     * @param withLoader 是否加入有用戶端與伺服器自動測試的載入器模組。
     * @param block 使用 fixture 根目錄的測試動作。
     */
    private fun withFixture(withLoader: Boolean, block: (File) -> Unit) {
        createTempDirectory("mahjongcraft-autotest-server-aggregate").toFile().apply {
            try {
                prepareFixture(this, withLoader)
                block(this)
            } finally {
                deleteRecursively()
            }
        }
    }

    private companion object {
        /** 假報告的資料夾名稱：符合報告時間的格式，而且晚於任何一次測試的開始時間。 */
        const val REPORT_FOLDER = "2099-01-01_00-00-00"
    }
}
