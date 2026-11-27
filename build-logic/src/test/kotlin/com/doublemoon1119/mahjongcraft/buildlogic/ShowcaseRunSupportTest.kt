package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證宣傳截圖模式的開關、傳給開發用戶端的設定，以及與自動測試互斥。 */
class ShowcaseRunSupportTest {
    /** 只有 true（不分大小寫）開啟宣傳截圖模式。 */
    @Test
    fun `only true turns the showcase on`() {
        listOf("true", "TRUE", " True ").forEach { value -> assertTrue(isShowcaseEnabled(value), "value '$value'") }
        listOf(null, "", "false", "all", "yes").forEach { value -> assertFalse(isShowcaseEnabled(value), "value '$value'") }
    }

    /** 開啟時登記準備任務，並使用獨立的執行目錄、視窗大小與系統屬性。 */
    @Test
    fun `the showcase prepares its own run directory`() = withFixture { root ->
        val result = runner(root, "-PmahjongcraftShowcase=true").build()

        assertTrue(result.output.contains("prepare registered: true"), result.output)
        assertTrue(result.output.contains("run directory: run/showcase-client"), result.output)
        assertTrue(result.output.contains("arguments: [--username, MahjongShowcase, --width, 2560, --height, 1440]"), result.output)
        assertTrue(result.output.contains("showcase property: true"), result.output)
        val titlePath = root.resolve("docs/assets/branding/title.png").absolutePath
        assertTrue(result.output.contains("title property: $titlePath"), result.output)
    }

    /** 沒有開啟時不登記任務，開發執行與平常相同。 */
    @Test
    fun `without the switch nothing changes`() = withFixture { root ->
        val result = runner(root).build()

        assertTrue(result.output.contains("prepare registered: false"), result.output)
        assertTrue(result.output.contains("run directory: run/client"), result.output)
        assertTrue(result.output.contains("arguments: []"), result.output)
    }

    /** 與自動測試一起開啟時建置失敗。 */
    @Test
    fun `the showcase and the automated test cannot run together`() = withFixture { root ->
        val result = runner(root, "-PmahjongcraftShowcase=true", "-PmahjongcraftAutotest=true").buildAndFail()

        assertTrue(result.output.contains("mahjongcraftShowcase and mahjongcraftAutotest cannot be used in the same build"), result.output)
    }

    /**
     * 建立含一個載入器模組的 fixture，模組以 `runClient` 執行開發用戶端。
     *
     * @param root fixture 根目錄。
     */
    private fun prepareFixture(root: File) {
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
            """
            rootProject.name = "showcase-fixture"
            include(":minecraft_test_fabric")
            project(":minecraft_test_fabric").projectDir = file("platform/v1.21.1/fabric")
            gradle.ext.set("mahjongcraft.targetJavaToolchain", [":minecraft_test_fabric": 17])
            gradle.ext.set("mahjongcraft.targetJavaRelease", [":minecraft_test_fabric": 17])
            """.trimIndent() + "\n",
        )
        root.resolve("build.gradle").writeText(
            """
            version = "0.0.0-dev"
            ext.projectId = "mahjongcraft"
            ext.projectDisplayName = "MahjongCraft"
            """.trimIndent() + "\n",
        )
        root.resolve("platform/v1.21.1/fabric/build.gradle").apply { parentFile.mkdirs() }.writeText(
            """
            import com.doublemoon1119.mahjongcraft.buildlogic.MinecraftBuildSupportKt
            import com.doublemoon1119.mahjongcraft.buildlogic.ShowcaseRunSupportKt

            plugins {
                id("mahjongcraft.minecraft-loader")
            }

            tasks.register("runClient")
            ShowcaseRunSupportKt.registerShowcaseClientTasks(project, "runClient")

            def registered = provider { tasks.names.contains("prepareShowcaseClient") }
            def directory = MinecraftBuildSupportKt.clientRunDirectory(project)
            def arguments = MinecraftBuildSupportKt.showcaseClientArguments(project)
            def properties = MinecraftBuildSupportKt.showcaseSystemProperties(project)
            tasks.register("printShowcase") {
                doLast {
                    println("prepare registered: " + registered.get())
                    println("run directory: " + directory)
                    println("arguments: " + arguments)
                    println("showcase property: " + properties["mahjongcraft.showcase"])
                    println("title property: " + properties["mahjongcraft.showcase.title"])
                }
            }
            """.trimIndent() + "\n",
        )
    }

    /**
     * 建立執行 fixture 的 Gradle runner。
     *
     * @param root fixture 根目錄。
     * @param properties 額外的 Gradle 屬性參數。
     * @return 列出宣傳截圖設定的 runner。
     */
    private fun runner(root: File, vararg properties: String): GradleRunner = GradleRunner.create()
        .withProjectDir(root)
        .withPluginClasspath()
        .withArguments(listOf("printShowcase", "--stacktrace") + properties)
        .forwardOutput()

    /**
     * 建立並清理一次性的 fixture 專案。
     *
     * @param block 使用 fixture 根目錄的測試動作。
     */
    private fun withFixture(block: (File) -> Unit) {
        createTempDirectory("mahjongcraft-showcase").toFile().apply {
            try {
                prepareFixture(this)
                block(this)
            } finally {
                deleteRecursively()
            }
        }
    }
}
