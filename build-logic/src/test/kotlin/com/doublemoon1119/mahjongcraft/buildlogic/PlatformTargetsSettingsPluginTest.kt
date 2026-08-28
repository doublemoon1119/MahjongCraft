package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 以 Gradle TestKit 驗證 Settings plugin 的實際選擇行為。 */
class PlatformTargetsSettingsPluginTest {
    /** 完全未指定 target 時必須使用內建 core。 */
    @Test
    fun defaultsToCoreTarget() = withFixture { root ->
        prepareBuild(root)

        val result = runner(root).withArguments("selectedTarget").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":selectedTarget")?.outcome)
        assertTrue(result.output.contains("selected=core"))
    }

    /** CLI core target 必須覆寫 local Minecraft target。 */
    @Test
    fun explicitCoreOverridesLocalTarget() = withFixture { root ->
        prepareBuild(root)
        root.resolve("local.dev.properties").writeText("mahjongcraftTarget=minecraft-test\n")

        val result = runner(root).withArguments("selectedTarget", "-PmahjongcraftTarget=core").build()

        assertTrue(result.output.contains("selected=core"))
        assertTrue(result.output.contains("Target: core"))
    }

    /** 未知 target 必須列出合法選項並使 Settings 階段失敗。 */
    @Test
    fun rejectsUnknownTarget() = withFixture { root ->
        prepareBuild(root)

        val result = runner(root).withArguments("help", "-PmahjongcraftTarget=unknown").buildAndFail()

        assertTrue(result.output.contains("Unknown MahjongCraft target 'unknown'"))
        assertTrue(result.output.contains("core, minecraft-test"))
    }

    private fun prepareBuild(root: File) {
        root.resolve("gradle").mkdirs()
        root.toPath().resolve("platform/test").createDirectories()
        root.resolve("gradle/platform-targets.toml").writeText(
            """
            schema-version = 1

            [[targets]]
            id = "minecraft-test"
            platform = "minecraft"
            java-toolchain = 17
            java-release = 17
            modules = ["platform/test"]

            [targets.attributes]
            minecraft-version = "v1.20.1"
            loader = "fabric"
            """.trimIndent() + "\n",
        )
        root.resolve("settings.gradle.kts").writeText(
            """
            plugins { id("mahjongcraft.platform-targets") }
            rootProject.name = "fixture"
            """.trimIndent() + "\n",
        )
        root.resolve("build.gradle.kts").writeText(
            """
            tasks.register("selectedTarget") {
                doLast {
                    println("selected=" + gradle.extensions.extraProperties["mahjongcraft.selectedTargetId"])
                }
            }
            """.trimIndent() + "\n",
        )
        root.resolve("platform/test/build.gradle.kts").writeText("")
    }

    private fun runner(root: File): GradleRunner = GradleRunner.create()
        .withProjectDir(root)
        .withPluginClasspath()
        .forwardOutput()

    private fun withFixture(block: (File) -> Unit) {
        createTempDirectory("mahjongcraft-settings-test").toFile().apply {
            try {
                block(this)
            } finally {
                deleteRecursively()
            }
        }
    }
}
