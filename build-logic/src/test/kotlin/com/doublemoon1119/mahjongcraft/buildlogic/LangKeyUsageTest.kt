package com.doublemoon1119.mahjongcraft.buildlogic

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** [unusedLangKeys]、[stringLiterals]、[langFileKeys] 與 [productionSourceFiles] 的單元測試。 */
class LangKeyUsageTest {
    /** 以完整字串出現的 key 視為已使用，只出現部分片段的不算。 */
    @Test
    fun `keys must appear as whole string literals`() {
        val literals = stringLiterals("""val a = "mahjongcraft.hud.used"; val b = "mahjongcraft.hud.partial.suffix"""")

        assertEquals(
            listOf("mahjongcraft.hud.partial", "mahjongcraft.hud.unused"),
            unusedLangKeys(listOf("mahjongcraft.hud.used", "mahjongcraft.hud.partial", "mahjongcraft.hud.unused"), literals, "mahjongcraft"),
        )
    }

    /** Minecraft 依註冊 ID 組成的名稱，只要註冊 ID 的路徑或完整 ID 出現即可。 */
    @Test
    fun `registry derived names accept the registry path or full id`() {
        val keys = listOf("item.mahjongcraft.tile", "block.mahjongcraft.table", "entity.mahjongcraft.bot", "item.mahjongcraft.missing")
        val literals = setOf("tile", "mahjongcraft:table", "bot")

        assertEquals(listOf("item.mahjongcraft.missing"), unusedLangKeys(keys, literals, "mahjongcraft"))
    }

    /** 其他 Minecraft 慣例 key（例如成就、字幕）仍需以完整字串出現在資源中。 */
    @Test
    fun `other minecraft keys still need a full reference`() {
        val literals = stringLiterals("""{ "subtitle": "subtitles.mahjongcraft.tile.land" }""")

        assertEquals(
            listOf("advancements.mahjongcraft.root.title"),
            unusedLangKeys(listOf("subtitles.mahjongcraft.tile.land", "advancements.mahjongcraft.root.title"), literals, "mahjongcraft"),
        )
    }

    /** 跳脫的引號不會切斷字串。 */
    @Test
    fun `string literals keep escaped quotes inside`() {
        assertEquals(setOf("a \\\"b\\\" c", "d"), stringLiterals("""x("a \"b\" c", "d")"""))
    }

    /** 語系檔每行一個條目，取出 key。 */
    @Test
    fun `lang file keys are read line by line`() {
        val text = "{\n  \"a.first\": \"First\",\n  \"b.second\": \"Second \\\"quoted\\\"\"\n}\n"

        assertEquals(listOf("a.first", "b.second"), langFileKeys(text))
    }

    /** 只收正式 source set 的檔案，略過測試、build 輸出與語系檔。 */
    @Test
    fun `production source files skip tests builds and lang files`() {
        val root = Files.createTempDirectory("lang-key-usage").toFile()
        try {
            listOf(
                "module/src/main/kotlin/A.kt",
                "module/src/commonMain/kotlin/B.kt",
                "module/src/jvmMain/resources/data/c.json",
                "module/src/test/kotlin/D.kt",
                "module/src/commonTest/kotlin/E.kt",
                "module/build/generated/src/main/F.kt",
                "module/src/jvmMain/resources/assets/x/lang/en_us.json",
                "module/src/main/kotlin/G.txt",
            ).forEach { path -> root.resolve(path).apply { parentFile.mkdirs() }.writeText("") }

            assertEquals(
                listOf("module/src/commonMain/kotlin/B.kt", "module/src/jvmMain/resources/data/c.json", "module/src/main/kotlin/A.kt"),
                productionSourceFiles(root).map { it.relativeTo(root).invariantSeparatorsPath }.sorted(),
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
