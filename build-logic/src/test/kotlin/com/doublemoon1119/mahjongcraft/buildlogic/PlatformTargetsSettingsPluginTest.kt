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
        assertTrue(result.output.contains(":example-minecraft_common"), result.output)
        assertTrue(result.output.contains(":example-minecraft_v1.20.1_fabric"), result.output)
    }

    /** 未指定或明確關閉範例時，不應載入範例 project。 */
    @Test
    fun `does not load examples when disabled`() = withFixture { root ->
        prepareBuild(root)

        listOf(emptyList(), listOf("-PmahjongcraftExample=false")).forEach { flag ->
            val result = runner(root).withArguments(listOf("projects", "-PmahjongcraftTarget=minecraft-test") + flag).build()
            assertTrue(!result.output.contains(":example:example-mahjong-extension"), result.output)
            assertTrue(!result.output.contains(":example-minecraft_common"), result.output)
            assertTrue(!result.output.contains(":example-minecraft_v1.20.1_fabric"), result.output)
        }
    }

    /** 沒有宣告範例的正式 target 不能悄悄忽略啟用要求。 */
    @Test
    fun `rejects enabled examples when target has none`() = withFixture { root ->
        prepareBuild(root)
        val catalog = root.resolve("gradle/platform/minecraft/v1.20.1/targets.toml")
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

        assertTrue(first.output.contains(":example-minecraft_common"), first.output)
        assertTrue(second.output.contains(":example-minecraft_common"), second.output)
        assertTrue(second.output.contains("Configuration cache entry reused"), second.output)
    }

    /** 正式與範例的同名 Minecraft module 必須映射到不同的 Gradle project path。 */
    @Test
    fun `keeps formal and example minecraft project names distinct`() = withFixture { root ->
        prepareBuild(root)
        root.toPath().resolve("platform/minecraft/common").createDirectories()
        root.resolve("platform/minecraft/common/build.gradle.kts").writeText("")
        root.resolve("gradle/platform/minecraft/v1.20.1/targets.toml").apply { parentFile.mkdirs() }.writeText(
            root.resolve("gradle/platform/minecraft/v1.20.1/targets.toml").readText()
                .replace("modules = [\"platform/test\"]", "modules = [\"platform/test\", \"platform/minecraft/common\"]"),
        )

        val result = runner(root).withArguments(
            "projects",
            "-PmahjongcraftTarget=minecraft-test",
            "-PmahjongcraftExample=true",
        ).build()

        assertTrue(result.output.contains(":minecraft_common"), result.output)
        assertTrue(result.output.contains(":example-minecraft_common"), result.output)
    }

    /** 以逗號分隔的多個 target 載入模組的聯集，共用的模組只載入一次，各模組取得自己 target 的設定。 */
    @Test
    fun `loads the union of several targets`() = withFixture { root ->
        prepareBuild(root)
        addSecondTarget(root, javaToolchain = 17)

        val result = runner(root).withArguments(
            "selectedTarget",
            "projects",
            "-PmahjongcraftTarget=minecraft-test, minecraft-test-forge",
            "-PmahjongcraftExample=true",
        ).build()

        assertTrue(result.output.contains("selected=minecraft-test,minecraft-test-forge"), result.output)
        assertTrue(result.output.contains("Target: minecraft-test, minecraft-test-forge"), result.output)
        assertTrue(result.output.contains(":test"), result.output)
        assertTrue(result.output.contains(":test-forge"), result.output)
        assertTrue(result.output.contains(":shared"), result.output)
        assertTrue(result.output.contains(":example-minecraft_v1.20.1_fabric"), result.output)
        assertTrue(result.output.contains(":example-minecraft_v1.20.1_forge"), result.output)
        val toolchains = result.output.lines().single { it.startsWith("toolchains=") }
        listOf(":example:example-mahjong-extension", ":shared", ":test", ":test-forge").forEach { project ->
            assertTrue(toolchains.contains("$project=17"), toolchains)
        }
        // 共用模組在兩個 target 中執行環境提供的函式庫不同，沒有單一答案。
        assertTrue(result.output.contains(":shared=null"), result.output)
        assertTrue(result.output.contains(":test={kotlin=2.0.0}"), result.output)
        assertTrue(result.output.contains(":test-forge={kotlin=2.1.0}"), result.output)
    }

    /** 清單中的重複、空白項目與和其他 target 並列的 core 都使 Settings 階段失敗。 */
    @Test
    fun `rejects malformed target lists`() = withFixture { root ->
        prepareBuild(root)
        addSecondTarget(root, javaToolchain = 17)

        mapOf(
            "minecraft-test,minecraft-test" to "repeats",
            "minecraft-test,,minecraft-test-forge" to "separate target IDs with single commas",
            "core,minecraft-test" to "cannot be combined",
            "minecraft-test,unknown" to "Unknown MahjongCraft target 'unknown'",
        ).forEach { (selection, message) ->
            val result = runner(root).withArguments("help", "-PmahjongcraftTarget=$selection").buildAndFail()
            assertTrue(result.output.contains(message), result.output)
        }
    }

    /** 共用模組所屬的 target 使用不同的 Java 版本時，不能一起選擇。 */
    @Test
    fun `rejects shared modules with different java versions`() = withFixture { root ->
        prepareBuild(root)
        addSecondTarget(root, javaToolchain = 21)

        val result = runner(root).withArguments("help", "-PmahjongcraftTarget=minecraft-test,minecraft-test-forge").buildAndFail()

        assertTrue(result.output.contains("Module platform/shared belongs to selected targets with different java-toolchain"), result.output)
    }

    /** 選擇的 target 都沒有範例模組時，啟用範例仍然失敗。 */
    @Test
    fun `rejects enabled examples when no selected target has any`() = withFixture { root ->
        prepareBuild(root)
        addSecondTarget(root, javaToolchain = 17)
        val catalog = root.resolve("gradle/platform/minecraft/v1.20.1/targets.toml")
        catalog.writeText(catalog.readText().replace(Regex("example-modules = \\[.*?]", RegexOption.DOT_MATCHES_ALL), ""))

        val result = runner(root).withArguments(
            "help",
            "-PmahjongcraftTarget=minecraft-test,minecraft-test-forge",
            "-PmahjongcraftExample=true",
        ).buildAndFail()

        assertTrue(result.output.contains("does not declare any example modules"), result.output)
    }

    /**
     * 在 catalog 加入第二個 target，與原本的 target 共用 `platform/shared` 模組。
     *
     * @param root 測試專案根目錄。
     * @param javaToolchain 第二個 target 的 Java toolchain。
     */
    private fun addSecondTarget(root: File, javaToolchain: Int) {
        root.toPath().resolve("platform/test-forge").createDirectories()
        root.toPath().resolve("platform/shared").createDirectories()
        root.toPath().resolve("example/platform/minecraft/v1.20.1/forge").createDirectories()
        root.resolve("platform/test-forge/build.gradle.kts").writeText("")
        root.resolve("platform/shared/build.gradle.kts").writeText("")
        root.resolve("example/platform/minecraft/v1.20.1/forge/build.gradle.kts").writeText("")
        val catalog = root.resolve("gradle/platform/minecraft/v1.20.1/targets.toml")
        catalog.writeText(
            catalog.readText()
                .replace("modules = [\"platform/test\"]", "modules = [\"platform/test\", \"platform/shared\"]")
                .replace(
                    "loader = \"fabric\"\n",
                    "loader = \"fabric\"\n\n[targets.provided-versions]\nkotlin = \"2.0.0\"\n",
                ) +
                """

                [[targets]]
                id = "minecraft-test-forge"
                platform = "minecraft"
                java-toolchain = $javaToolchain
                java-release = 17
                modules = ["platform/test-forge", "platform/shared"]
                example-modules = [
                    "example/mahjong-extension",
                    "example/platform/minecraft/common",
                    "example/platform/minecraft/v1.20.1/forge",
                ]

                [targets.attributes]
                minecraft-version = "v1.20.1"
                loader = "forge"

                [targets.provided-versions]
                kotlin = "2.1.0"
                """.trimIndent() + "\n",
        )
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
        root.toPath().resolve("example/platform/minecraft/common").createDirectories()
        root.toPath().resolve("example/platform/minecraft/v1.20.1/fabric").createDirectories()
        root.resolve("gradle/platform/minecraft/v1.20.1/targets.toml").apply { parentFile.mkdirs() }.writeText(
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
                "example/platform/minecraft/common",
                "example/platform/minecraft/v1.20.1/fabric",
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
                    val extras = gradle.extensions.extraProperties
                    println("selected=" + (extras["mahjongcraft.selectedTargetIds"] as List<*>).joinToString(","))
                    println("toolchains=" + (extras["mahjongcraft.targetJavaToolchain"] as Map<*, *>).toSortedMap(compareBy { it.toString() }))
                    println("provided=" + (extras["mahjongcraft.targetProvidedVersions"] as Map<*, *>).toSortedMap(compareBy { it.toString() }))
                }
            }
            """.trimIndent() + "\n",
        )
        root.resolve("platform/test/build.gradle.kts").writeText("")
        root.resolve("example/mahjong-extension/build.gradle.kts").writeText("")
        root.resolve("example/platform/minecraft/common/build.gradle.kts").writeText("")
        root.resolve("example/platform/minecraft/v1.20.1/fabric/build.gradle.kts").writeText("")
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
