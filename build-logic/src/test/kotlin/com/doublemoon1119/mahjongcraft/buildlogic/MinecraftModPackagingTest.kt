package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 以 Gradle TestKit 驗證 Minecraft loader module 的 metadata 與產物隔離。 */
class MinecraftModPackagingTest {
    /** 未覆寫時沿用 root mod 的 ID、名稱、說明、圖示與 archive 名稱。 */
    @Test
    fun `defaults preserve formal mod packaging`() = withFixture { root ->
        prepareFixture(root)

        val result = runner(root, ":formal:processResources", ":formal:jar").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":formal:processResources")?.outcome)

        val metadata = root.resolve("platform/v1.20.1/fabric/build/resources/main/fabric.mod.json").readText()
        assertTrue(metadata.contains("\"id\": \"mahjongcraft\""), metadata)
        assertTrue(metadata.contains("\"name\": \"MahjongCraft\""), metadata)
        assertTrue(metadata.contains("\"description\": \"Bring Mahjong to Minecraft and play with your friends.\""), metadata)
        assertTrue(metadata.contains("\"icon\": \"assets/mahjongcraft/icon.png\""), metadata)
        assertTrue(root.resolve("platform/v1.20.1/fabric/build/resources/main/assets/mahjongcraft/icon.png").isFile)
        assertTrue(root.resolve("platform/v1.20.1/fabric/build/libs/mahjongcraft-fabric-1.20.1-0.0.0-dev.jar").isFile)
    }

    /** example 可覆寫自己的 metadata 與 archive 名稱，但不會改變正式 mod 的 root ID。 */
    @Test
    fun `example packaging can override metadata without changing formal id`() = withFixture { root ->
        prepareFixture(root)

        runner(root, ":formal:processResources", ":example:processResources", ":example:jar").build()

        val formalMetadata = root.resolve("platform/v1.20.1/fabric/build/resources/main/fabric.mod.json").readText()
        val exampleMetadata = root.resolve("example/v1.20.1/fabric/build/resources/main/fabric.mod.json").readText()
        assertTrue(formalMetadata.contains("\"id\": \"mahjongcraft\""), formalMetadata)
        assertTrue(exampleMetadata.contains("\"id\": \"mahjongcraft_example\""), exampleMetadata)
        assertTrue(exampleMetadata.contains("\"name\": \"MahjongCraft Example\""), exampleMetadata)
        assertTrue(exampleMetadata.contains("\"description\": \"Example extension mod.\""), exampleMetadata)
        assertTrue(exampleMetadata.contains("\"icon\": \"assets/mahjongcraft_example/icon.png\""), exampleMetadata)
        assertTrue(root.resolve("example/v1.20.1/fabric/build/resources/main/assets/mahjongcraft_example/icon.png").isFile)
        assertTrue(root.resolve("example/v1.20.1/fabric/build/libs/mahjongcraft-example-0.0.0-dev.jar").isFile)
        assertTrue(!root.resolve("platform/v1.20.1/fabric/build/resources/main/assets/mahjongcraft_example/icon.png").exists())
    }

    /** processResources 可在 configuration cache 中重用，避免範例建置破壞快取。 */
    @Test
    fun `packaging reuses configuration cache`() = withFixture { root ->
        prepareFixture(root)

        val first = runner(root, ":example:processResources", "--configuration-cache").build()
        val second = runner(root, ":example:processResources", "--configuration-cache").build()

        assertTrue(first.output.contains("Configuration cache entry stored"), first.output)
        assertTrue(second.output.contains("Configuration cache entry reused"), second.output)
    }

    /** 建立不含 Minecraft Loom 的最小正式／範例 loader fixture。 */
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

            rootProject.name = "packaging-fixture"
            include(":formal", ":example")
            project(":formal").projectDir = file("platform/v1.20.1/fabric")
            project(":example").projectDir = file("example/v1.20.1/fabric")
            gradle.ext.set("mahjongcraft.targetJavaToolchain", 17)
            gradle.ext.set("mahjongcraft.targetJavaRelease", 17)
            """.trimIndent() + "\n",
        )
        root.resolve("build.gradle").writeText(
            """
            version = "0.0.0-dev"
            ext.projectId = "mahjongcraft"
            ext.projectDisplayName = "MahjongCraft"
            allprojects {
                version = rootProject.version
            }
            """.trimIndent() + "\n",
        )
        root.resolve("platform/v1.20.1/fabric/src/main/resources/fabric.mod.json").apply {
            parentFile.mkdirs()
            writeText(metadataTemplate())
        }
        root.resolve("example/v1.20.1/fabric/src/main/resources/fabric.mod.json").apply {
            parentFile.mkdirs()
            writeText(metadataTemplate())
        }
        root.resolve("platform/v1.20.1/fabric/build.gradle").writeText(loaderBuildScript())
        root.resolve("example/v1.20.1/fabric/build.gradle").writeText(
            loaderBuildScript() +
                """

                minecraftModPackaging {
                    modId = "mahjongcraft_example"
                    displayName = "MahjongCraft Example"
                    description = "Example extension mod."
                    archiveBaseName = "mahjongcraft-example"
                }
                """.trimIndent() + "\n",
        )
    }

    /** Fixture 中使用的 metadata placeholder。 */
    private fun metadataTemplate(): String =
        """
        {
          "id": "${'$'}{id}",
          "version": "${'$'}{version}",
          "name": "${'$'}{name}",
          "description": "${'$'}{description}",
          "icon": "assets/${'$'}{id}/icon.png"
        }
        """.trimIndent() + "\n"

    /** Fixture loader project 的最小 Gradle script。 */
    private fun loaderBuildScript(): String =
        """
        plugins {
            id("mahjongcraft.minecraft-loader")
        }
        """.trimIndent() + "\n"

    /** 建立執行 fixture 的 Gradle runner。 */
    private fun runner(root: File, vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(root)
        .withPluginClasspath()
        .withArguments(*arguments, "--stacktrace")
        .forwardOutput()

    /** 建立並清理一次性的 fixture 專案。 */
    private fun withFixture(block: (File) -> Unit) {
        createTempDirectory("mahjongcraft-packaging-test").toFile().apply {
            try {
                block(this)
            } finally {
                deleteRecursively()
            }
        }
    }
}
