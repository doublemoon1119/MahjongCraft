package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 以 Gradle TestKit 驗證事件 API ABI convention 的基準、過濾範圍與設定快取行為。 */
class EventApiAbiPluginTest {
    /** JVM 模組只將 `api.event` 套件的公開型別納入基準，並在 `check` 中執行 ABI 檢查。 */
    @Test
    fun `jvm event api baseline is checked and reused`() = withFixture { root ->
        writeJvmFixture(root)

        runner(root, "updateKotlinAbi").build()

        val baseline = root.resolve("api/fixture.api")
        assertTrue(baseline.isFile, "The JVM ABI baseline should be generated")
        val baselineText = baseline.readText()
        assertTrue(baselineText.contains("fixture/api/event/PublicEvent"))
        assertTrue(!baselineText.contains("fixture/internal/InternalPublicType"))

        val first = runner(root, "check", "--configuration-cache").build()
        val second = runner(root, "check", "--configuration-cache").build()

        assertTaskRan(first, ":checkKotlinAbi")
        assertTaskRan(second, ":checkKotlinAbi")
        assertTrue(first.output.contains("Configuration cache entry stored"), first.output)
        assertTrue(second.output.contains("Configuration cache entry reused"), second.output)
    }

    /** 非 API 套件的公開函式簽名變更不應改變事件 API ABI 檢查結果。 */
    @Test
    fun `jvm non api public change passes abi check`() = withFixture { root ->
        writeJvmFixture(root)
        runner(root, "updateKotlinAbi").build()
        root.resolve(INTERNAL_SOURCE).writeText(internalSource("fun changed(): String = \"changed\""))

        assertTaskRan(runner(root, "checkKotlinAbi").build(), ":checkKotlinAbi")
    }

    /** API 套件的公開函式簽名變更應使 ABI 檢查失敗。 */
    @Test
    fun `jvm api signature change fails abi check`() = withFixture { root ->
        writeJvmFixture(root)
        runner(root, "updateKotlinAbi").build()
        root.resolve(API_SOURCE).writeText(apiSource("fun stable(value: Int): String = value.toString()"))

        val result = runner(root, "checkKotlinAbi").buildAndFail()

        assertTrue(result.task(":checkKotlinAbi")?.outcome == TaskOutcome.FAILED, result.output)
        assertTrue(result.output.contains("stable"), result.output)
    }

    /** Kotlin Multiplatform 的 JVM target 也應建立事件 API ABI 檢查任務與基準。 */
    @Test
    fun `multiplatform jvm target generates event api baseline`() = withFixture { root ->
        writeMultiplatformFixture(root)

        runner(root, "updateKotlinAbi").build()

        val baseline = root.resolve("api/fixture.api")
        assertTrue(baseline.isFile, "The KMP ABI baseline should be generated")
        assertTrue(baseline.readText().contains("fixture/api/event/PublicEvent"))
        assertTaskRan(runner(root, "checkKotlinAbi").build(), ":checkKotlinAbi")
    }

    /** 建立帶有 JVM Kotlin 外掛與事件 API convention 的 TestKit fixture。
     *
     * @param root fixture 根目錄。
     */
    private fun writeJvmFixture(root: File) {
        writeCommonFixture(root)
        root.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.jvm")
                id("mahjongcraft.event-api-abi")
            }

            repositories {
                mavenCentral()
            }

            kotlin {
                jvmToolchain(17)
            }
            """.trimIndent() + "\n",
        )
        writeSources(root, "src/main/kotlin")
    }

    /** 建立帶有 JVM target 的 Kotlin Multiplatform TestKit fixture。
     *
     * @param root fixture 根目錄。
     */
    private fun writeMultiplatformFixture(root: File) {
        writeCommonFixture(root)
        root.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform")
                id("mahjongcraft.event-api-abi")
            }

            repositories {
                mavenCentral()
            }

            kotlin {
                jvm()
                jvmToolchain(17)
            }
            """.trimIndent() + "\n",
        )
        writeSources(root, "src/commonMain/kotlin")
    }

    /** 建立 fixture 的 Gradle 設定與 plugin resolution repository。
     *
     * @param root fixture 根目錄。
     */
    private fun writeCommonFixture(root: File) {
        root.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                }
            }

            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                }
            }

            rootProject.name = "fixture"
            """.trimIndent() + "\n",
        )
    }

    /** 寫入 API 與非 API 套件的最小 Kotlin 原始碼。
     *
     * @param root fixture 根目錄。
     * @param sourceDirectory Kotlin source set 的相對目錄。
     */
    private fun writeSources(
        root: File,
        sourceDirectory: String,
    ) {
        root.resolve(sourceDirectory).resolve("fixture/api/event/PublicEvent.kt").also {
            it.parentFile.mkdirs()
            it.writeText(apiSource("fun stable(): String = \"stable\""))
        }
        root.resolve(sourceDirectory).resolve("fixture/internal/InternalPublicType.kt").also {
            it.parentFile.mkdirs()
            it.writeText(internalSource("val value: String = \"initial\""))
        }
    }

    /** 產生事件 API fixture 原始碼。
     *
     * @param functionDeclaration 公開成員函式宣告。
     * @return 可編譯的 Kotlin 原始碼。
     */
    private fun apiSource(functionDeclaration: String): String =
        "package fixture.api.event\n\nclass PublicEvent {\n    $functionDeclaration\n}\n"

    /** 產生非事件 API fixture 原始碼。
     *
     * @param declaration 非 API 類別內的公開成員宣告。
     * @return 可編譯的 Kotlin 原始碼。
     */
    private fun internalSource(declaration: String): String =
        "package fixture.internal\n\nclass InternalPublicType {\n    $declaration\n}\n"

    /** 建立執行 fixture Gradle 任務的 TestKit runner。
     *
     * @param root fixture 根目錄。
     * @param arguments 要傳給 Gradle 的任務與選項。
     * @return 已設定 fixture 與 plugin classpath 的 runner。
     */
    private fun runner(
        root: File,
        vararg arguments: String,
    ): GradleRunner = GradleRunner.create()
        .withProjectDir(root)
        .withPluginClasspath()
        .withArguments(*arguments)
        .forwardOutput()

    /** 確認指定 Gradle 任務存在並成功完成。
     *
     * @param result TestKit 執行結果。
     * @param taskPath 要檢查的 Gradle 任務路徑。
     */
    private fun assertTaskRan(
        result: BuildResult,
        taskPath: String,
    ) {
        assertNotNull(result.task(taskPath), "Expected task $taskPath to run")
        assertTrue(
            result.task(taskPath)?.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE),
            result.output,
        )
    }

    /** 建立並在測試結束後清理臨時 fixture。
     *
     * @param block 使用 fixture 根目錄的測試動作。
     */
    private fun withFixture(block: (File) -> Unit) {
        createTempDirectory("mahjongcraft-event-api-abi-test").toFile().apply {
            try {
                block(this)
            } finally {
                deleteRecursively()
            }
        }
    }

    /** 測試 fixture 的固定原始碼路徑。 */
    private companion object {
        /** JVM fixture 的事件 API Kotlin 原始碼相對路徑。 */
        const val API_SOURCE = "src/main/kotlin/fixture/api/event/PublicEvent.kt"

        /** JVM fixture 的非事件 API Kotlin 原始碼相對路徑。 */
        const val INTERNAL_SOURCE = "src/main/kotlin/fixture/internal/InternalPublicType.kt"
    }
}
