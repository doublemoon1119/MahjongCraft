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
    fun `defaults to core target`() = withFixture { root ->
        prepareBuild(root)

        val result = runner(root).withArguments("selectedTarget").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":selectedTarget")?.outcome)
        assertTrue(result.output.contains("selected=core"))
    }

    /** CLI core target 必須覆寫 local Minecraft target。 */
    @Test
    fun `explicit core overrides local target`() = withFixture { root ->
        prepareBuild(root)
        root.resolve("local.dev.properties").writeText("mahjongcraftTarget=minecraft-test\n")

        val result = runner(root).withArguments("selectedTarget", "-PmahjongcraftTarget=core").build()

        assertTrue(result.output.contains("selected=core"))
        assertTrue(result.output.contains("Target: core"))
    }

    /** 未知 target 必須列出合法選項並使 Settings 階段失敗。 */
    @Test
    fun `rejects unknown target`() = withFixture { root ->
        prepareBuild(root)

        val result = runner(root).withArguments("help", "-PmahjongcraftTarget=unknown").buildAndFail()

        assertTrue(result.output.contains("Unknown MahjongCraft target 'unknown'"))
        assertTrue(result.output.contains("core, minecraft-test"))
    }

    /** 明確啟用範例時，Settings 應載入範例 project 並使用穩定 project path。 */
    @Test
    fun `loads examples only when explicitly enabled`() = withFixture { root ->
        prepareBuild(root)

        val result = runner(root).withArguments(
            "projects",
            "-PmahjongcraftTarget=minecraft-test",
            "-PmahjongcraftExample=true",
        ).build()

        assertTrue(result.output.contains(":example:example-mahjong-extension"), result.output)
        assertTrue(result.output.contains(":minecraft_example_common"), result.output)
        assertTrue(result.output.contains(":minecraft_example_v1.20.1_fabric"), result.output)
    }

    /** 未指定或明確關閉範例時，不應載入範例 project。 */
    @Test
    fun `does not load examples when disabled`() = withFixture { root ->
        prepareBuild(root)

        listOf(emptyList(), listOf("-PmahjongcraftExample=false")).forEach { flag ->
            val result = runner(root).withArguments(listOf("projects", "-PmahjongcraftTarget=minecraft-test") + flag).build()
            assertTrue(!result.output.contains(":example:example-mahjong-extension"), result.output)
            assertTrue(!result.output.contains(":minecraft_example_common"), result.output)
            assertTrue(!result.output.contains(":minecraft_example_v1.20.1_fabric"), result.output)
        }
    }

    /** 沒有宣告範例的正式 target 不能悄悄忽略啟用要求。 */
    @Test
    fun `rejects enabled examples when target has none`() = withFixture { root ->
        prepareBuild(root)
        val catalog = root.resolve("gradle/platform-targets.toml")
        catalog.writeText(catalog.readText().replace(Regex("example-modules = \\[.*?]", RegexOption.DOT_MATCHES_ALL), ""))

        val result = runner(root).withArguments(
            "projects",
            "-PmahjongcraftTarget=minecraft-test",
            "-PmahjongcraftExample=true",
        ).buildAndFail()
        assertTrue(result.output.contains("does not declare any example modules"), result.output)
    }

    /** 範例開關只接受字面值 true 或 false。 */
    @Test
    fun `rejects invalid examples property`() = withFixture { root ->
        prepareBuild(root)

        listOf("", "TRUE", "1", " true ").forEach { value ->
            val result = runner(root).withArguments("help", "-PmahjongcraftExample=$value").buildAndFail()
            assertTrue(result.output.contains("expected true or false"), result.output)
        }
    }

    /** core target 沒有範例模組時，明確啟用範例應失敗。 */
    @Test
    fun `rejects examples with core target`() = withFixture { root ->
        prepareBuild(root)

        val result = runner(root).withArguments("help", "-PmahjongcraftExample=true").buildAndFail()

        assertTrue(result.output.contains("require a platform target"), result.output)
    }

    /** target 清單的 JSON 也應列出範例模組。 */
    @Test
    fun `lists example modules in json`() = withFixture { root ->
        prepareBuild(root)

        val result = runner(root).withArguments("listPlatformTargets", "--format=json").build()

        assertTrue(result.output.contains("\"exampleModules\""), result.output)
        assertTrue(result.output.contains("example/mahjong-extension"), result.output)
    }

    /** 範例開關在 configuration cache 重用時仍維持相同的 project mapping。 */
    @Test
    fun `reuses configuration cache with examples enabled`() = withFixture { root ->
        prepareBuild(root)
        val arguments = arrayOf(
            "projects",
            "--configuration-cache",
            "-PmahjongcraftTarget=minecraft-test",
            "-PmahjongcraftExample=true",
        )

        val first = runner(root).withArguments(*arguments).build()
        val second = runner(root).withArguments(*arguments).build()

        assertTrue(first.output.contains(":minecraft_example_common"), first.output)
        assertTrue(second.output.contains(":minecraft_example_common"), second.output)
        assertTrue(second.output.contains("Configuration cache entry reused"), second.output)
    }

    /**
     * 建立 Settings plugin 測試所需的 catalog 與 Gradle 專案。
     *
     * @param root 測試專案根目錄。
     */
    private fun prepareBuild(root: File) {
        root.resolve("gradle").mkdirs()
        root.toPath().resolve("platform/test").createDirectories()
        root.toPath().resolve("example/mahjong-extension").createDirectories()
        root.toPath().resolve("platform/minecraft/example/common").createDirectories()
        root.toPath().resolve("platform/minecraft/example/v1.20.1/fabric").createDirectories()
        root.resolve("gradle/platform-targets.toml").writeText(
            """
            schema-version = 1

            [[targets]]
            id = "minecraft-test"
            platform = "minecraft"
            java-toolchain = 17
            java-release = 17
            modules = ["platform/test"]
            example-modules = [
                "example/mahjong-extension",
                "platform/minecraft/example/common",
                "platform/minecraft/example/v1.20.1/fabric",
            ]

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
            plugins {
                id("mahjongcraft.target-management")
            }
            tasks.register("selectedTarget") {
                doLast {
                    println("selected=" + gradle.extensions.extraProperties["mahjongcraft.selectedTargetId"])
                }
            }
            """.trimIndent() + "\n",
        )
        root.resolve("platform/test/build.gradle.kts").writeText("")
        root.resolve("example/mahjong-extension/build.gradle.kts").writeText("")
        root.resolve("platform/minecraft/example/common/build.gradle.kts").writeText("")
        root.resolve("platform/minecraft/example/v1.20.1/fabric/build.gradle.kts").writeText("")
    }

    /**
     * 建立執行測試 fixture 的 Gradle runner。
     *
     * @param root 測試專案根目錄。
     * @return 已設定專案目錄與 plugin classpath 的 runner。
     */
    private fun runner(root: File): GradleRunner = GradleRunner.create()
        .withProjectDir(root)
        .withPluginClasspath()
        .forwardOutput()

    /**
     * 建立並在測試完成後刪除暫存專案。
     *
     * @param block 使用暫存專案根目錄的測試動作。
     */
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
