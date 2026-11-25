package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

/** 驗證總任務在有範例模組時，以範例的用戶端測試代替載入器模組自己的用戶端測試。 */
class AutotestClientCoverageTest {
    /** 沒有範例模組時，載入器模組的用戶端測試列入總任務。 */
    @Test
    fun `loader client test joins the aggregate without an example`() = withFixture(withExample = false) { root ->
        val result = runner(root).build()

        assertTrue(result.output.contains("loader client in aggregate: true"), result.output)
    }

    /** 有範例模組時，載入器模組的用戶端測試不列入總任務，範例的用戶端測試列入。 */
    @Test
    fun `example client test replaces the loader client test`() = withFixture(withExample = true) { root ->
        val result = runner(root).build()

        assertTrue(result.output.contains("loader client in aggregate: false"), result.output)
        assertTrue(result.output.contains("example client in aggregate: true"), result.output)
    }

    /**
     * 建立含一個載入器模組、可選範例模組的 fixture；兩者都以 `runClient` 執行用戶端自動測試。
     *
     * @param root fixture 根目錄。
     * @param withExample 是否加入範例模組。
     */
    private fun prepareFixture(root: File, withExample: Boolean) {
        root.resolve("gradle/libs.versions.toml").apply { parentFile.mkdirs() }.writeText(
            """
            [versions]
            core-java-toolchain = "17"
            core-java-release = "17"
            kotlin-api-version = "2.4"
            """.trimIndent() + "\n",
        )
        root.resolve("docs/assets/branding/logo.png").apply { parentFile.mkdirs() }.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47))
        val projects = listOfNotNull(
            ":minecraft_test_fabric" to "platform/v1.20.1/fabric",
            if (withExample) ":example-minecraft_test_fabric" to "example/platform/v1.20.1/fabric" else null,
        )
        root.resolve("settings.gradle").writeText(
            buildString {
                appendLine("rootProject.name = \"autotest-coverage-fixture\"")
                projects.forEach { (path, directory) ->
                    appendLine("include(\"$path\")")
                    appendLine("project(\"$path\").projectDir = file(\"$directory\")")
                }
                val javaVersions = projects.joinToString { (path, _) -> "\"$path\": 17" }
                appendLine("gradle.ext.set(\"mahjongcraft.targetJavaToolchain\", [$javaVersions])")
                appendLine("gradle.ext.set(\"mahjongcraft.targetJavaRelease\", [$javaVersions])")
            },
        )
        root.resolve("build.gradle").writeText(
            """
            version = "0.0.0-dev"
            ext.projectId = "mahjongcraft"
            ext.projectDisplayName = "MahjongCraft"
            """.trimIndent() + "\n",
        )
        projects.forEach { (path, directory) ->
            val label = if (path.startsWith(":example-")) "example" else "loader"
            root.resolve("$directory/build.gradle").apply { parentFile.mkdirs() }.writeText(
                """
                plugins {
                    id("mahjongcraft.minecraft-loader")
                }

                tasks.register("runClient")
                com.doublemoon1119.mahjongcraft.buildlogic.AutotestRunSupportKt.registerAutotestClientTasks(project, "runClient")

                def inAggregate = provider { tasks.names.contains("autotestTarget") }
                tasks.register("printCoverage") {
                    def joined = inAggregate
                    doLast {
                        println("$label client in aggregate: " + joined.get())
                    }
                }
                """.trimIndent() + "\n",
            )
        }
    }

    /**
     * 建立執行 fixture 的 Gradle runner。
     *
     * @param root fixture 根目錄。
     * @return 開啟自動測試並列出各模組是否列入總任務的 runner。
     */
    private fun runner(root: File): GradleRunner = GradleRunner.create()
        .withProjectDir(root)
        .withPluginClasspath()
        .withArguments("printCoverage", "-PmahjongcraftAutotest=true", "--stacktrace")
        .forwardOutput()

    /**
     * 建立並清理一次性的 fixture 專案。
     *
     * @param withExample 是否加入範例模組。
     * @param block 使用 fixture 根目錄的測試動作。
     */
    private fun withFixture(withExample: Boolean, block: (File) -> Unit) {
        createTempDirectory("mahjongcraft-autotest-coverage").toFile().apply {
            try {
                prepareFixture(this, withExample)
                block(this)
            } finally {
                deleteRecursively()
            }
        }
    }
}
