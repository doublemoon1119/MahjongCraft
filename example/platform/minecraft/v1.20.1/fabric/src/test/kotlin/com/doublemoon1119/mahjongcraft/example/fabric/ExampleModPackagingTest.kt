package com.doublemoon1119.mahjongcraft.example.fabric

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 檢查實際重新映射的兩份 mod JAR，確保範例內容不滲入正式產物。 */
class ExampleModPackagingTest {
    /** 開發用執行環境須帶入本體內建規則與執行套件，不只包含對外 API。 */
    @Test
    fun `development runtime includes main mod implementation dependencies`() {
        listOf(
            "com/doublemoon1119/mahjongcraft/bundled/BundledMahjongExtensions.class",
            "org/koin/core/Koin.class",
            "com/akuleshov7/ktoml/Toml.class",
            "org/sqlite/JDBC.class",
        ).forEach { path ->
            assertTrue(javaClass.classLoader.getResources(path).hasMoreElements(), "Missing runtime class: $path")
        }
    }

    /** 三層範例合併成單一 mod，既不遺漏共用類別，也不重複包入本體與相依套件。 */
    @Test
    fun `example jar contains all layers without bundling main mod dependencies`() {
        ZipFile(System.getProperty("mahjongcraft.exampleJar")).use { jar ->
            val paths = jar.entries().asSequence().map { it.name }.toList()
            listOf(
                "com/doublemoon1119/mahjongcraft/example/ExampleMahjongExtension.class",
                "com/doublemoon1119/mahjongcraft/example/minecraft/ExampleMinecraftMahjongExtension.class",
                "com/doublemoon1119/mahjongcraft/example/fabric/ExampleFabricMahjongExtension.class",
                "com/doublemoon1119/mahjongcraft/example/fabric/ExampleMod.class",
                "com/doublemoon1119/mahjongcraft/example/fabric/ExampleModClient.class",
                "fabric.mod.json",
                "assets/mahjongcraft_example/icon.png",
                "assets/mahjongcraft_example/lang/en_us.json",
                "assets/mahjongcraft_example/lang/zh_tw.json",
                "assets/mahjongcraft_example/lang/zh_cn.json",
                "assets/mahjongcraft_example/lang/ja_jp.json",
                "assets/mahjongcraft_example/models/item/mahjong_tile/mahjong_tile_inverted_one_man.json",
                "assets/mahjongcraft_example/textures/item/mahjong_tile/mahjong_tile_inverted_one_man.png",
                "assets/mahjongcraft_example/textures/item/mahjong_tile/mahjong_tile_cover.png",
                "assets/mahjongcraft_example/textures/item/mahjong_tile/mahjong_tile_unknown.png",
                "assets/mahjongcraft_example/textures/font/tile_emoji.png",
                "assets/minecraft/font/default.json",
                "assets/mahjongcraft_example/blockstates/chair.json",
                "assets/mahjongcraft_example/models/block/chair.json",
                "assets/mahjongcraft_example/models/item/chair.json",
                "assets/mahjongcraft_example/models/item/cushion.json",
                "data/mahjongcraft_example/loot_tables/blocks/chair.json",
            ).forEach { path -> assertEquals(1, paths.count { it == path }, path) }
            listOf(
                "com/doublemoon1119/mahjongcraft/logic/",
                "com/doublemoon1119/mahjongcraft/flow/",
                "com/doublemoon1119/mahjongcraft/platform/",
                "kotlin/",
                "kotlinx/",
                "org/koin/",
                "META-INF/jars/",
            ).forEach { prefix -> assertFalse(paths.any { it.startsWith(prefix) }, prefix) }
            val metadata = Json.parseToJsonElement(jar.getInputStream(jar.getEntry("fabric.mod.json")).reader().readText()).jsonObject
            assertEquals("mahjongcraft_example", metadata.getValue("id").jsonPrimitive.content)
            assertTrue("mahjongcraft" in metadata.getValue("depends").jsonObject)
            assertFabricDependencyRequirements(metadata)
            assertEquals(
                "com.doublemoon1119.mahjongcraft.example.fabric.ExampleModClient",
                metadata.getValue("entrypoints").jsonObject.getValue("client").jsonArray.single().jsonPrimitive.content,
            )
        }
    }

    /** 正式 mod 即使與範例一起建置，也不能含有範例類別或資源。 */
    @Test
    fun `main jar never contains example classes or resources`() {
        ZipFile(System.getProperty("mahjongcraft.mainJar")).use { jar ->
            val paths = jar.entries().asSequence().map { it.name }.toList()
            assertFalse(paths.any { it.startsWith("com/doublemoon1119/mahjongcraft/example/") })
            assertFalse(paths.any { it.startsWith("assets/mahjongcraft_example/") })
            assertFalse(paths.any { it.startsWith("data/mahjongcraft_example/") })
            assertFalse(paths.any { it.contains("example", ignoreCase = true) && it.endsWith(".jar") })
            val metadata = Json.parseToJsonElement(jar.getInputStream(jar.getEntry("fabric.mod.json")).reader().readText()).jsonObject
            assertEquals("mahjongcraft", metadata.getValue("id").jsonPrimitive.content)
            assertFabricDependencyRequirements(metadata)
            assertFalse("mahjongcraft_example" in metadata.getValue("depends").jsonObject)
        }
    }

    /** 確認 Fabric metadata 的執行依賴已注入實際需求，而不是留下模板 placeholder。 */
    private fun assertFabricDependencyRequirements(metadata: JsonObject) {
        val dependencies = metadata.getValue("depends").jsonObject
        mapOf(
            "fabricloader" to ">=0.19.0",
            "fabric" to "*",
            "minecraft" to "1.20.1",
            "fabric-language-kotlin" to ">=1.13.14",
        ).forEach { (id, requirement) ->
            assertEquals(requirement, dependencies.getValue(id).jsonPrimitive.content, id)
        }
        assertFalse(
            dependencies.values.any { it.jsonPrimitive.content.contains("\${") },
            dependencies.toString(),
        )
    }
}
