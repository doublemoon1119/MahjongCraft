package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 以 TestKit 驗證自訂 KDoc 規則能由兩種 Kotlin convention plugin 載入並執行。 */
class KDocRulesPluginTest {
    /** JVM convention 可執行自訂規則，且 `check` 不會隱式執行完整的 `detektAll`。 */
    @Test
    fun `jvm convention loads KDoc rules`() = withFixture { root ->
        prepareFixture(root, KOTLIN_JVM_PLUGIN)

        val result = runner(root, "detektAll").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":detektAll")?.outcome)

        val check = runner(root, "check", "--dry-run").build()
        assertFalse(check.output.contains(":detektAll"), check.output)

        val runtimeClasspath = runner(root, "dependencies", "--configuration", "runtimeClasspath").build()
        assertFalse(runtimeClasspath.output.contains("mahjongcraft-detekt-rules"), runtimeClasspath.output)
    }

    /** KMP convention 的 JVM source set 也能執行自訂規則。 */
    @Test
    fun `multiplatform convention loads KDoc rules`() = withFixture { root ->
        prepareFixture(root, KOTLIN_MULTIPLATFORM_PLUGIN)
        val commonSource = root.resolve("src/commonMain/kotlin/Example.kt")
        commonSource.parentFile.mkdirs()
        root.resolve(SOURCE_FILE).copyTo(commonSource)
        root.resolve(SOURCE_FILE).delete()
        root.resolve("build.gradle.kts").appendText(
            text = """

            kotlin {
                jvm()
            }
            """.trimIndent() + "\n",
        )

        val result = runner(root, "detektAll").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":detektAll")?.outcome)
    }

    /** KDoc 開頭直接接描述文字時，規則應使 detektAll 失敗並指出違規。 */
    @Test
    fun `reports text on KDoc opening line`() = withFixture { root ->
        prepareFixture(root, KOTLIN_JVM_PLUGIN)
        root.resolve(SOURCE_FILE).writeText(
            text = """
            /** 不應與開頭標記放在同一行。
             * 這仍然是多行 KDoc。
             */
            class Example
            """.trimIndent() + "\n",
        )

        val result = runner(root, "detektAll").buildAndFail()
        assertTrue(result.output.contains("KDocOpeningLine"), result.output)
    }

    /** KDoc 描述與第一個標籤之間缺少空行時，規則應使 detektAll 失敗。 */
    @Test
    fun `reports missing blank line before tag`() = withFixture { root ->
        prepareFixture(root, KOTLIN_JVM_PLUGIN)
        root.resolve(SOURCE_FILE).writeText(
            text = """
            /**
             * 描述。
             * @property value 值。
             */
            class Example(
                val value: String,
            )
            """.trimIndent() + "\n",
        )

        val result = runner(root, "detektAll").buildAndFail()
        assertTrue(result.output.contains("KDocTagSpacing"), result.output)
    }

    /** 自訂規則的 detektAll 可以在 configuration cache 中重用。 */
    @Test
    fun `detektAll reuses configuration cache`() = withFixture { root ->
        prepareFixture(root, KOTLIN_JVM_PLUGIN)

        val first = runner(root, "detektAll", "--configuration-cache").build()
        val second = runner(root, "detektAll", "--configuration-cache").build()

        assertTrue(first.output.contains("Configuration cache entry stored"), first.output)
        assertTrue(second.output.contains("Configuration cache entry reused"), second.output)
    }

    /**
     * 建立套用指定 convention plugin 的 Detekt fixture。
     *
     * @param root fixture 根目錄。
     * @param pluginId 要套用的 Kotlin convention plugin ID。
     */
    private fun prepareFixture(
        root: File,
        pluginId: String,
    ) {
        val source = root.resolve(SOURCE_FILE)
        source.parentFile.mkdirs()
        source.writeText(
            text = """
            /**
             * 合法的類別說明。
             *
             * @property value 合法的屬性說明。
             */
            class Example(
                val value: String,
            )
            """.trimIndent() + "\n",
        )
        root.resolve("config/detekt").toPath().createDirectories()
        root.resolve("config/detekt/detekt.yml").writeText(
            text = """
            config:
              validation: true

            mahjongcraft:
              active: true
              KDocOpeningLine:
                active: true
              KDocTagSpacing:
                active: true
            """.trimIndent() + "\n",
        )
        root.resolve("gradle").toPath().createDirectories()
        root.resolve("gradle/libs.versions.toml").writeText(
            text = """
            [versions]
            core-java-toolchain = "17"
            core-java-release = "17"
            """.trimIndent() + "\n",
        )
        root.resolve("settings.gradle.kts").writeText(
            text = """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            dependencyResolutionManagement {
                repositories {
                    flatDir {
                        dirs(file(System.getProperty("mahjongcraft.detektRulesJar")).parentFile)
                    }
                    mavenCentral()
                }
            }

            rootProject.name = "detekt-rules-fixture"
            """.trimIndent() + "\n",
        )
        root.resolve("build.gradle.kts").writeText(
            text = """
            plugins {
                id("$pluginId")
            }
            """.trimIndent() + "\n",
        )
    }

    /**
     * 建立帶有預建 Detekt rules JAR 路徑的 TestKit runner。
     *
     * @param root fixture 根目錄。
     * @param arguments 傳給 Gradle 的命令列參數。
     * @return 已設定 fixture 與 plugin classpath 的 runner。
     */
    private fun runner(
        root: File,
        vararg arguments: String,
    ): GradleRunner = GradleRunner.create()
        .withProjectDir(root)
        .withPluginClasspath()
        .withArguments(
            *arguments,
            "--stacktrace",
            "-Dmahjongcraft.detektRulesJar=${detektRulesJar().absolutePath}",
        )
        .forwardOutput()

    /** 取得由 build-logic 測試任務傳入的自訂規則 JAR。 */
    private fun detektRulesJar(): File =
        System.getProperty("mahjongcraft.detektRulesJar")?.let(::File)
            ?: error("mahjongcraft.detektRulesJar system property is required")

    /**
     * 建立並清理一次性的 Gradle fixture。
     *
     * @param block 使用 fixture 的測試工作。
     */
    private fun withFixture(block: (File) -> Unit) {
        createTempDirectory("mahjongcraft-kdoc-rules-test").toFile().apply {
            try {
                block(this)
            } finally {
                deleteRecursively()
            }
        }
    }

    private companion object {
        /** fixture Kotlin source 的相對路徑。 */
        const val SOURCE_FILE = "src/main/kotlin/Example.kt"

        /** Kotlin JVM convention plugin ID。 */
        const val KOTLIN_JVM_PLUGIN = "mahjongcraft.kotlin-jvm"

        /** Kotlin Multiplatform convention plugin ID。 */
        const val KOTLIN_MULTIPLATFORM_PLUGIN = "mahjongcraft.kotlin-multiplatform"
    }
}
