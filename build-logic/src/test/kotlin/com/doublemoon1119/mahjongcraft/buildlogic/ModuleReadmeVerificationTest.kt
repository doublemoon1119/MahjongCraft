package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 以 TestKit 驗證 README 覆蓋範圍與錯誤診斷。 */
class ModuleReadmeVerificationTest {
    /** 完整的 loaded、catalog、build-logic 與索引文件集合必須通過。 */
    @Test
    fun acceptsCompleteReadmeSet() = withFixture { root ->
        prepareBuild(root)
        requiredDirectories(root).forEach { it.resolve("README.md").writeText("# Test\n") }

        val result = runner(root).withArguments("verifyModuleReadmes").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyModuleReadmes")?.outcome)
    }

    /** 缺少任一類文件時必須一次列出 loaded、catalog、build-logic 與索引路徑。 */
    @Test
    fun reportsEveryMissingReadmeCategory() = withFixture { root ->
        prepareBuild(root)
        root.resolve("README.md").writeText("# Test\n")

        val result = runner(root).withArguments("verifyModuleReadmes").buildAndFail()

        assertTrue(result.output.contains("loaded/README.md"))
        assertTrue(result.output.contains("platform/catalog-only/README.md"))
        assertTrue(result.output.contains("build-logic/README.md"))
        assertTrue(result.output.contains("platform/README.md"))
        assertTrue(result.output.contains("platform/minecraft/README.md"))
    }

    /** 大小寫不敏感的檔案系統也不能讓錯誤檔名通過。 */
    @Test
    fun rejectsIncorrectReadmeCapitalization() = withFixture { root ->
        prepareBuild(root)
        requiredDirectories(root).forEach { it.resolve("README.md").writeText("# Test\n") }
        root.resolve("loaded/README.md").delete()
        root.resolve("loaded/readme.md").writeText("# Wrong name\n")

        val result = runner(root).withArguments("verifyModuleReadmes").buildAndFail()

        assertTrue(result.output.contains("loaded/README.md"))
    }

    private fun prepareBuild(root: File) {
        requiredDirectories(root).forEach { it.toPath().createDirectories() }
        root.resolve("gradle/platform-targets.toml").writeText(
            """
            schema-version = 1

            [[targets]]
            id = "test-target"
            platform = "test"
            java-toolchain = 17
            java-release = 17
            modules = ["platform/catalog-only"]
            """.trimIndent() + "\n",
        )
        root.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "fixture"
            include("loaded")
            """.trimIndent() + "\n",
        )
        root.resolve("build.gradle.kts").writeText("plugins { id(\"mahjongcraft.repository-verification\") }\n")
        root.resolve("loaded/build.gradle.kts").writeText("")
    }

    private fun requiredDirectories(root: File): List<File> = listOf(
        root,
        root.resolve("loaded"),
        root.resolve("platform/catalog-only"),
        root.resolve("build-logic"),
        root.resolve("platform"),
        root.resolve("platform/minecraft"),
        root.resolve("gradle"),
    )

    private fun runner(root: File): GradleRunner = GradleRunner.create()
        .withProjectDir(root)
        .withPluginClasspath()
        .forwardOutput()

    private fun withFixture(block: (File) -> Unit) {
        createTempDirectory("mahjongcraft-readme-test").toFile().apply {
            try {
                block(this)
            } finally {
                deleteRecursively()
            }
        }
    }
}
