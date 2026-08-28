package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import java.io.File
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證 target catalog、輸入優先序與本機設定更新。 */
class PlatformTargetCatalogTest {
    /** 合法 catalog 應完整解析 target 與 Java policy。 */
    @Test
    fun loadsValidCatalog() = withFixture { root ->
        createModule(root, "platform/example")
        val targets = writeCatalog(
            root,
            target("minecraft-example", "platform/example"),
        ).let { PlatformTargetCatalog.load(it, root) }

        assertEquals(1, targets.size)
        assertEquals("minecraft-example", targets.single().id)
        assertEquals(17, targets.single().javaToolchain)
        assertEquals(17, targets.single().javaRelease)
        assertEquals("v1.20.1", targets.single().attributes["minecraft-version"])
    }

    /** 非 Minecraft 平台不需要提供 Minecraft 專屬欄位。 */
    @Test
    fun loadsPlatformNeutralTarget() = withFixture { root ->
        createModule(root, "platform/desktop")
        val catalog = writeCatalog(
            root,
            """
            [[targets]]
            id = "desktop-release"
            platform = "desktop"
            java-toolchain = 21
            java-release = 21
            modules = ["platform/desktop"]

            [targets.attributes]
            package-format = "zip"
            """.trimIndent() + "\n",
        )

        val target = PlatformTargetCatalog.load(catalog, root).single()

        assertEquals("desktop", target.platform)
        assertEquals(mapOf("package-format" to "zip"), target.attributes)
    }

    /** 重複 target ID 必須立即失敗。 */
    @Test
    fun rejectsDuplicateTargetIds() = withFixture { root ->
        createModule(root, "platform/one")
        createModule(root, "platform/two")
        val catalog = writeCatalog(
            root,
            target("duplicate", "platform/one") + target("duplicate", "platform/two"),
        )

        assertFailsWith<GradleException> { PlatformTargetCatalog.load(catalog, root) }
    }

    /** 不合理的 Java version 必須立即失敗。 */
    @Test
    fun rejectsInvalidJavaVersion() = withFixture { root ->
        createModule(root, "platform/example")
        val catalog = writeCatalog(
            root,
            target("minecraft-example", "platform/example").replace("java-release = 17", "java-release = 0"),
        )

        assertFailsWith<GradleException> { PlatformTargetCatalog.load(catalog, root) }
    }

    /** 指向不存在 module 的 catalog 必須立即失敗。 */
    @Test
    fun rejectsMissingModule() = withFixture { root ->
        val catalog = writeCatalog(root, target("minecraft-example", "platform/missing"))

        assertFailsWith<GradleException> { PlatformTargetCatalog.load(catalog, root) }
    }

    /** 已知平台缺少必要 attributes 時必須立即失敗。 */
    @Test
    fun rejectsMissingKnownPlatformAttribute() = withFixture { root ->
        createModule(root, "platform/example")
        val catalog = writeCatalog(
            root,
            target("minecraft-example", "platform/example").replace("loader = \"fabric\"", ""),
        )

        assertFailsWith<GradleException> { PlatformTargetCatalog.load(catalog, root) }
    }

    /** Gradle property 應依序覆寫環境變數與 local fallback。 */
    @Test
    fun resolvesTargetByDocumentedPrecedence() {
        assertEquals(
            "cli",
            PlatformTargetCatalog.resolveSelectedTargetId(" cli ", "environment", "local"),
        )
        assertEquals(
            "environment",
            PlatformTargetCatalog.resolveSelectedTargetId(" ", "environment", "local"),
        )
        assertEquals(
            "local",
            PlatformTargetCatalog.resolveSelectedTargetId(null, null, "local"),
        )
        assertEquals(
            PlatformTargetCatalog.CORE_TARGET_ID,
            PlatformTargetCatalog.resolveSelectedTargetId(null, " ", null),
        )
    }

    /** 切換與清除 target 時必須保留其他 local properties。 */
    @Test
    fun updatesOnlyTargetProperty() = withFixture { root ->
        val localFile = root.resolve("local.dev.properties")
        localFile.writeText("customProperty=kept\nmahjongcraftTarget=old\n")

        updateLocalProperty(localFile, "new-target")
        assertTrue(localFile.readText().contains("customProperty=kept"))
        assertTrue(localFile.readText().contains("mahjongcraftTarget=new-target"))
        assertFalse(localFile.readText().contains("mahjongcraftTarget=old"))

        updateLocalProperty(localFile, null)
        assertEquals("customProperty=kept", localFile.readText().trim())
    }

    private fun target(id: String, module: String): String = """
        [[targets]]
        id = "$id"
        platform = "minecraft"
        java-toolchain = 17
        java-release = 17
        modules = ["$module"]

        [targets.attributes]
        minecraft-version = "v1.20.1"
        loader = "fabric"
    """.trimIndent() + "\n"

    private fun writeCatalog(root: File, targets: String): File = root.resolve("platform-targets.toml").apply {
        writeText("schema-version = 1\n\n$targets")
    }

    private fun createModule(root: File, path: String) {
        root.toPath().resolve(path).createDirectories()
    }

    private fun withFixture(block: (File) -> Unit) {
        createTempDirectory("mahjongcraft-target-test").toFile().apply {
            try {
                block(this)
            } finally {
                deleteRecursively()
            }
        }
    }
}
