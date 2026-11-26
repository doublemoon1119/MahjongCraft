package com.doublemoon1119.mahjongcraft.example.forge

import com.electronwill.nightconfig.core.Config
import com.electronwill.nightconfig.toml.TomlFormat
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 檢查轉成 Forge 執行時名稱後的兩份 mod JAR，確保範例內容不滲入正式產物。 */
class ExampleModPackagingTest {
    /** 各層範例合併成單一 mod，既不遺漏共用類別，也不重複包入本體與相依套件。 */
    @Test
    fun `example jar contains all layers without bundling main mod dependencies`() {
        ZipFile(System.getProperty("mahjongcraft.exampleJar")).use { jar ->
            val paths = jar.entries().asSequence().map { it.name }.toList()
            listOf(
                "com/doublemoon1119/mahjongcraft/example/ExampleMahjongExtension.class",
                "com/doublemoon1119/mahjongcraft/example/minecraft/ExampleMinecraftMahjongExtension.class",
                "com/doublemoon1119/mahjongcraft/example/minecraft/mod/ExampleNativeMahjongExtension.class",
                "com/doublemoon1119/mahjongcraft/example/minecraft/mod/ExampleNativeMahjongClientExtension.class",
                "com/doublemoon1119/mahjongcraft/example/minecraft/mod/character/ExampleActorEntity.class",
                "com/doublemoon1119/mahjongcraft/example/minecraft/mod/furniture/block/ExampleBenchBlock.class",
                "com/doublemoon1119/mahjongcraft/example/forge/ExampleMod.class",
                "com/doublemoon1119/mahjongcraft/example/forge/ExampleModClient.class",
                "META-INF/mods.toml",
                "pack.mcmeta",
                "assets/mahjongcraft_example/icon.png",
                "assets/mahjongcraft_example/lang/en_us.json",
                "assets/mahjongcraft_example/blockstates/bench.json",
                "assets/mahjongcraft_example/models/item/actor.json",
                "data/mahjongcraft_example/loot_tables/blocks/chair.json",
            ).forEach { path -> assertEquals(1, paths.count { it == path }, path) }
            listOf(
                "com/doublemoon1119/mahjongcraft/logic/",
                "com/doublemoon1119/mahjongcraft/flow/",
                "com/doublemoon1119/mahjongcraft/platform/",
                "kotlin/",
                "kotlinx/",
                "org/koin/",
                "META-INF/jarjar/",
            ).forEach { prefix -> assertFalse(paths.any { it.startsWith(prefix) }, prefix) }
        }
    }

    /** 範例以 `modproperties` 宣告擴充，並要求同一版本的本體先載入。 */
    @Test
    fun `example metadata declares the extensions and requires the main mod`() {
        val metadata = readModsToml(System.getProperty("mahjongcraft.exampleJar"))
        val mod = metadata.get<List<Config>>("mods").single()
        assertEquals("mahjongcraft_example", mod.get<String>("modId"))
        val properties = metadata.get<Config>(listOf("modproperties", "mahjongcraft_example"))
        assertEquals(
            expected = "com.doublemoon1119.mahjongcraft.example.minecraft.mod.ExampleNativeMahjongExtension",
            actual = properties.get<String>(listOf("mahjongcraft:extension")),
        )
        assertEquals(
            expected = "com.doublemoon1119.mahjongcraft.example.minecraft.mod.ExampleNativeMahjongClientExtension",
            actual = properties.get<String>(listOf("mahjongcraft:client_extension")),
        )
        val dependencies = metadata.get<List<Config>>(listOf("dependencies", "mahjongcraft_example")).associateBy { it.get<String>("modId") }
        val mainMod = dependencies.getValue("mahjongcraft")
        assertEquals("[${mod.get<String>("version")}]", mainMod.get<String>("versionRange"))
        assertEquals("AFTER", mainMod.get<String>("ordering"))
        assertTrue(mainMod.get<Boolean>("mandatory"))
        assertForgeDependencyRequirements(dependencies)
    }

    /** 正式 mod 即使與範例一起建置，也不能含有範例類別或資源。 */
    @Test
    fun `main jar never contains example classes or resources`() {
        val mainJar = System.getProperty("mahjongcraft.mainJar")
        ZipFile(mainJar).use { jar ->
            val paths = jar.entries().asSequence().map { it.name }.toList()
            assertFalse(paths.any { it.startsWith("com/doublemoon1119/mahjongcraft/example/") })
            assertFalse(paths.any { it.startsWith("assets/mahjongcraft_example/") })
            assertFalse(paths.any { it.startsWith("data/mahjongcraft_example/") })
            assertFalse(paths.any { it.contains("example", ignoreCase = true) && it.endsWith(".jar") })
        }
        val metadata = readModsToml(mainJar)
        assertEquals("mahjongcraft", metadata.get<List<Config>>("mods").single().get<String>("modId"))
        val dependencies = metadata.get<List<Config>>(listOf("dependencies", "mahjongcraft")).associateBy { it.get<String>("modId") }
        assertFalse("mahjongcraft_example" in dependencies)
        assertForgeDependencyRequirements(dependencies)
    }

    /**
     * 讀出 JAR 中的 `META-INF/mods.toml`。
     *
     * @param jarPath JAR 的路徑。
     * @return 解析後的設定。
     */
    private fun readModsToml(jarPath: String): Config = ZipFile(jarPath).use { jar ->
        TomlFormat.instance().createParser().parse(jar.getInputStream(jar.getEntry("META-INF/mods.toml")).reader().readText())
    }

    /**
     * 確認 Forge metadata 的執行依賴已注入實際需求，而不是留下模板 placeholder。
     *
     * @param dependencies 依 mod ID 排列的依賴宣告。
     */
    private fun assertForgeDependencyRequirements(dependencies: Map<String, Config>) {
        mapOf(
            "forge" to "[47,)",
            "minecraft" to "[1.20.1,1.20.2)",
            "kotlinforforge" to "[4.13,)",
        ).forEach { (id, requirement) ->
            assertEquals(requirement, dependencies.getValue(id).get<String>("versionRange"), id)
        }
        assertFalse(dependencies.values.any { it.get<String>("versionRange").contains("\${") }, dependencies.toString())
    }
}
