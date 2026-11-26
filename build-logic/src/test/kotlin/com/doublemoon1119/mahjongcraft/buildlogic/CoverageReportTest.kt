package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 驗證測試覆蓋率量測的開關與根專案的合併。
 *
 * - 沒有開關時不套用 Kover，平常的建置不出現 Kover 的任務。
 * - 開啟時根專案合併各 Kotlin 模組，測試輔助模組除外。
 */
class CoverageReportTest {
    /** 沒有指定或指定為 false 時不量測，指定為 true 時量測。 */
    @Test
    fun `switch is off unless set to true`() {
        assertFalse(coverageEnabled(property = null))
        assertFalse(coverageEnabled(property = "false"))
        assertTrue(coverageEnabled(property = "true"))
    }

    /** 只接受 true 與 false。 */
    @Test
    fun `switch rejects other values`() {
        assertFailsWith<GradleException> { coverageEnabled(property = "yes") }
    }

    /** 沒有開關時，模組與根專案都沒有 Kover 的任務。 */
    @Test
    fun `no kover tasks without the switch`() = withFixture { root ->
        val rootTasks = runner(root, "tasks", "--all").build().output

        assertFalse(rootTasks.contains("koverHtmlReport"), rootTasks)
        assertFalse(rootTasks.contains("app:koverHtmlReport"), rootTasks)
    }

    /** 開啟時根專案合併一般模組，測試輔助模組不套用 Kover，也不在合併之列。 */
    @Test
    fun `root merges every module except testing support`() = withFixture { root ->
        val merged = runner(root, "dependencies", "--configuration", "kover", "-P$COVERAGE_PROPERTY=true").build().output
        val tasks = runner(root, "tasks", "--all", "-P$COVERAGE_PROPERTY=true").build().output

        assertTrue(merged.contains("--- project app"), merged)
        assertFalse(merged.contains("project fakes"), merged)
        assertTrue(tasks.contains("app:koverHtmlReport"), tasks)
        assertFalse(tasks.contains("testing:fakes:koverHtmlReport"), tasks)
    }

    /** 開關的值不是 true 或 false 時建置失敗。 */
    @Test
    fun `invalid switch value fails the build`() = withFixture { root ->
        val result = runner(root, "tasks", "-P$COVERAGE_PROPERTY=yes").buildAndFail()

        assertTrue(result.output.contains("Invalid -P$COVERAGE_PROPERTY='yes'"), result.output)
    }

    /**
     * 建立一個一般模組 `:app` 與一個測試輔助模組 `:testing:fakes` 的 fixture，兩者都套用 Kotlin JVM convention。
     *
     * @param block 使用 fixture 根目錄的測試內容。
     */
    private fun withFixture(block: (File) -> Unit) {
        val root = createTempDirectory("mahjongcraft-coverage-test").toFile()
        try {
            root.resolve("gradle").mkdirs()
            root.resolve("gradle/libs.versions.toml").writeText(
                text = """
                [versions]
                core-java-toolchain = "17"
                core-java-release = "17"
                kotlin-api-version = "2.4"
                """.trimIndent() + "\n",
            )
            root.resolve("settings.gradle.kts").writeText(
                text = """
                rootProject.name = "coverage-fixture"
                include(":app")
                include(":testing:fakes")
                """.trimIndent() + "\n",
            )
            root.resolve("build.gradle.kts").writeText(
                text = """
                import com.doublemoon1119.mahjongcraft.buildlogic.registerCoverageAggregate

                plugins {
                    id("mahjongcraft.kotlin-jvm") apply false
                }

                registerCoverageAggregate()
                """.trimIndent() + "\n",
            )
            listOf("app", "testing/fakes").forEach { directory ->
                root.resolve(directory).mkdirs()
                root.resolve("$directory/build.gradle.kts").writeText(
                    text = """
                    plugins {
                        id("mahjongcraft.kotlin-jvm")
                    }
                    """.trimIndent() + "\n",
                )
            }
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    /**
     * 建立使用 build-logic 外掛的 TestKit runner。
     *
     * @param root fixture 根目錄。
     * @param arguments Gradle 參數。
     * @return runner。
     */
    private fun runner(root: File, vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(root)
        .withPluginClasspath()
        .withArguments(*arguments, "--stacktrace")
        .forwardOutput()
}
