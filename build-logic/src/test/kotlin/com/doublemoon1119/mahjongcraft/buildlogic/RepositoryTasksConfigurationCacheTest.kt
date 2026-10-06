package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 以 TestKit 驗證 repository 驗證與 target 管理任務可在 configuration cache 下執行與重用。 */
class RepositoryTasksConfigurationCacheTest {
    /** `check` 串接的驗證任務在第一次執行時存入 configuration cache，第二次直接重用且結果相同。 */
    @Test
    fun verificationTasksReuseConfigurationCache() = withFixture { root ->
        prepareBuild(root)

        val first = runner(root, "check", "--configuration-cache").build()
        val second = runner(root, "check", "--configuration-cache").build()

        VERIFICATION_TASKS.forEach { task ->
            assertEquals(TaskOutcome.SUCCESS, first.task(task)?.outcome, task)
            assertEquals(TaskOutcome.SUCCESS, second.task(task)?.outcome, task)
        }
        assertStoredThenReused(first, second)
    }

    /** 從 configuration cache 重用時仍讀到最新的版本設定，版本不符照樣失敗。 */
    @Test
    fun versionPolicyStillFailsWhenVersionsMismatch() = withFixture { root ->
        prepareBuild(root)
        root.resolve("mahjong-logic/build.gradle.kts").writeText("version = \"9.9.9\"\n")

        val result = runner(root, "verifyProjectVersionPolicy", "--configuration-cache").buildAndFail()

        assertTrue(result.output.contains(":mahjong-logic: expected 1.0.0, found 9.9.9"))
    }

    /** target 管理任務可在 configuration cache 下執行與重用。 */
    @Test
    fun targetTasksReuseConfigurationCache() = withFixture { root ->
        prepareBuild(root)

        val first = runner(root, "listPlatformTargets", "--configuration-cache").build()
        val second = runner(root, "listPlatformTargets", "--configuration-cache").build()
        runner(root, "switchTarget", "-PtoTarget=test-target", "--configuration-cache").build()
        val switched = root.resolve("local.dev.properties").readText()
        runner(root, "clearTarget", "--configuration-cache").build()

        assertTrue(first.output.contains("test-target"))
        assertStoredThenReused(first, second)
        assertTrue(switched.contains("mahjongcraftTarget=test-target"))
        assertTrue(!root.resolve("local.dev.properties").exists())
    }

    private fun assertStoredThenReused(
        first: BuildResult,
        second: BuildResult,
    ) {
        assertTrue(first.output.contains("Configuration cache entry stored"), first.output)
        assertTrue(second.output.contains("Configuration cache entry reused"), second.output)
    }

    private fun prepareBuild(root: File) {
        listOf("gradle", "mahjong-logic", "build-logic", "platform/minecraft", "platform/catalog-only", LANG_DIRECTORY)
            .forEach { root.resolve(it).toPath().createDirectories() }
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
        root.resolve("gradle/libs.versions.toml").writeText(
            """
            [versions]
            minecraft-mod-version = "1.0.0"
            logic-version = "1.0.0"
            flow-version = "1.0.0"
            ai-version = "1.0.0"
            extension-api-version = "1.0.0"
            bundled-extensions-version = "1.0.0"
            """.trimIndent() + "\n",
        )
        root.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "fixture"
            include("mahjong-logic")
            """.trimIndent() + "\n",
        )
        root.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("mahjongcraft.repository-verification")
                id("mahjongcraft.target-management")
            }
            version = "0.0.0-dev"
            """.trimIndent() + "\n",
        )
        root.resolve("mahjong-logic/build.gradle.kts").writeText("version = \"1.0.0\"\n")
        listOf(".", "mahjong-logic", "build-logic", "platform", "platform/minecraft", "platform/catalog-only")
            .forEach { root.resolve(it).resolve("README.md").writeText("# Test\n") }
        root.resolve(LANG_DIRECTORY).resolve("en_us.json").writeText("{\n  \"a.key\": \"A\",\n  \"b.key\": \"B\"\n}\n")
    }

    private fun runner(
        root: File,
        vararg arguments: String,
    ): GradleRunner = GradleRunner.create()
        .withProjectDir(root)
        .withPluginClasspath()
        .withArguments(*arguments)
        .forwardOutput()

    private fun withFixture(block: (File) -> Unit) {
        createTempDirectory("mahjongcraft-configuration-cache-test").toFile().apply {
            try {
                block(this)
            } finally {
                deleteRecursively()
            }
        }
    }

    private companion object {
        /** fixture 中的 Minecraft 語系檔目錄。 */
        const val LANG_DIRECTORY = "platform/minecraft/common/src/jvmMain/resources/assets/mahjongcraft/lang"

        /** `check` 串接的所有驗證任務。 */
        val VERIFICATION_TASKS = listOf(
            ":verifyNoDocsTempReferences",
            ":verifyProjectVersionPolicy",
            ":verifyModuleReadmes",
            ":verifyMinecraftLangFileKeyOrder",
        )
    }
}
