package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 驗證開發執行時可選載入模組的開關解析，以及載入器模組只加入選中的組合。 */
class MinecraftDevModsTest {
    /** 沒給開關或只有空白時不選任何組合；名稱去掉空白與重複，保留順序。 */
    @Test
    fun `parse trims names and ignores empty values`() {
        assertEquals(emptySet(), MinecraftDevMods.parse(null))
        assertEquals(emptySet(), MinecraftDevMods.parse(""))
        assertEquals(emptySet(), MinecraftDevMods.parse(" , "))
        assertEquals(listOf("iris", "modmenu"), MinecraftDevMods.parse(" iris , modmenu,iris ").toList())
    }

    /** 名稱只接受小寫英文字母、數字與連字號。 */
    @Test
    fun `parse rejects malformed names`() {
        listOf("Iris", "mod menu", "-iris", "iris_shaders").forEach { value ->
            assertFailsWith<GradleException>(value) { MinecraftDevMods.parse(value) }
        }
    }

    /** 選中卻沒有宣告的組合報錯，並列出可用的組合。 */
    @Test
    fun `unknown groups are rejected with the available ones`() {
        val error = assertFailsWith<GradleException> { MinecraftDevMods.requireDeclared(":fabric", setOf("iris", "missing"), setOf("iris", "modmenu")) }
        assertTrue(error.message.orEmpty().contains("missing"), error.message)
        assertTrue(error.message.orEmpty().contains("Available: iris, modmenu"), error.message)
        MinecraftDevMods.requireDeclared(":fabric", setOf("iris"), setOf("iris", "modmenu"))
    }

    /** 沒給開關時不加入任何模組。 */
    @Test
    fun `no switch adds nothing`() = withFixture { root ->
        val result = runner(root, "printDevRuntime").build()

        assertTrue(result.output.contains("dev runtime: []"), result.output)
    }

    /** 只加入選中組合的模組，未選中的組合不加入。 */
    @Test
    fun `selected groups add only their dependencies`() = withFixture { root ->
        val result = runner(root, "printDevRuntime", "-P${MinecraftDevMods.PROPERTY_NAME}=modmenu").build()

        assertTrue(result.output.contains("dev runtime: [example:modmenu:1]"), result.output)
    }

    /** 可以同時選多個組合。 */
    @Test
    fun `several groups can be selected`() = withFixture { root ->
        val result = runner(root, "printDevRuntime", "-P${MinecraftDevMods.PROPERTY_NAME}=iris,modmenu").build()

        assertTrue(result.output.contains("dev runtime: [example:sodium:1, example:iris:1, example:modmenu:1]"), result.output)
    }

    /** 選了沒有宣告的組合時建置失敗，訊息列出可用的組合。 */
    @Test
    fun `unknown group fails the build`() = withFixture { root ->
        val result = runner(root, "printDevRuntime", "-P${MinecraftDevMods.PROPERTY_NAME}=missing").buildAndFail()

        assertTrue(result.output.contains("Unknown development mod groups for :loader: missing. Available: iris, modmenu"), result.output)
    }

    /** 建立含一個載入器模組的 fixture；模組以自訂設定代替 Fabric Loom 的開發執行設定。 */
    private fun prepareFixture(root: File) {
        root.resolve("gradle/libs.versions.toml").apply {
            parentFile.mkdirs()
            writeText(
                """
                [versions]
                core-java-toolchain = "17"
                core-java-release = "17"
                """.trimIndent() + "\n",
            )
        }
        root.resolve("docs/assets/branding/logo.png").apply {
            parentFile.mkdirs()
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47))
        }
        root.resolve("settings.gradle").writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                }
            }

            rootProject.name = "dev-mods-fixture"
            include(":loader")
            project(":loader").projectDir = file("platform/v1.20.1/fabric")
            gradle.ext.set("mahjongcraft.targetJavaToolchain", 17)
            gradle.ext.set("mahjongcraft.targetJavaRelease", 17)
            """.trimIndent() + "\n",
        )
        root.resolve("build.gradle").writeText(
            """
            version = "0.0.0-dev"
            ext.projectId = "mahjongcraft"
            ext.projectDisplayName = "MahjongCraft"
            """.trimIndent() + "\n",
        )
        root.resolve("platform/v1.20.1/fabric/build.gradle").apply {
            parentFile.mkdirs()
            writeText(
                """
                plugins {
                    id("mahjongcraft.minecraft-loader")
                }

                configurations.create("devRuntime")

                devMods {
                    configurationName = "devRuntime"
                    group("iris", "example:sodium:1", "example:iris:1")
                    group("modmenu", "example:modmenu:1")
                }

                def devRuntimeDependencies = provider {
                    configurations.devRuntime.dependencies.collect { "${'$'}{it.group}:${'$'}{it.name}:${'$'}{it.version}" }
                }

                tasks.register("printDevRuntime") {
                    def listed = devRuntimeDependencies
                    doLast {
                        println("dev runtime: " + listed.get())
                    }
                }
                """.trimIndent() + "\n",
            )
        }
    }

    /** 建立執行 fixture 的 Gradle runner。 */
    private fun runner(root: File, vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(root)
        .withPluginClasspath()
        .withArguments(*arguments, "--stacktrace")
        .forwardOutput()

    /** 建立並清理一次性的 fixture 專案。 */
    private fun withFixture(block: (File) -> Unit) {
        createTempDirectory("mahjongcraft-dev-mods-test").toFile().apply {
            try {
                prepareFixture(this)
                block(this)
            } finally {
                deleteRecursively()
            }
        }
    }
}
