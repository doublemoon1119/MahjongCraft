package com.doublemoon1119.mahjongcraft.buildlogic

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/** [jsonFormatBoundaryViolations] 的正式程式碼範圍與違規位置測試。 */
class JsonFormatBoundaryTest {
    /** 正式 source set 引用 JSON 套件時，回報檔案相對路徑與行號。 */
    @Test
    fun `reports json usage in production sources`() = withFixture { root ->
        val project = root.resolve("guarded")
        project.write("src/commonMain/kotlin/Codec.kt", "package sample\n\nimport kotlinx.serialization.json.Json\n")
        project.write("src/jvmMain/kotlin/Inline.kt", "val format = kotlinx.serialization.json.Json\n")

        assertEquals(
            expected = listOf("guarded/src/commonMain/kotlin/Codec.kt:3", "guarded/src/jvmMain/kotlin/Inline.kt:1"),
            actual = jsonFormatBoundaryViolations(root, listOf(project)),
        )
    }

    /** 測試 source set 可以使用 JSON 驗證序列化結果，不列為違規。 */
    @Test
    fun `ignores test sources`() = withFixture { root ->
        val project = root.resolve("guarded")
        project.write("src/commonTest/kotlin/CodecTest.kt", "import kotlinx.serialization.json.Json\n")
        project.write("src/test/kotlin/OtherTest.kt", "import kotlinx.serialization.json.Json\n")
        project.write("src/commonMain/kotlin/Model.kt", "import kotlinx.serialization.Serializable\n")

        assertEquals(emptyList(), jsonFormatBoundaryViolations(root, listOf(project)))
    }

    /** 註解提到 JSON 套件只是說明，不使用 JSON，不列為違規。 */
    @Test
    fun `ignores comments`() = withFixture { root ->
        val project = root.resolve("guarded")
        project.write(
            "src/commonMain/kotlin/Doc.kt",
            "/**\n * 不使用 kotlinx.serialization.json。\n */\n// kotlinx.serialization.json\nclass Doc\n",
        )

        assertEquals(emptyList(), jsonFormatBoundaryViolations(root, listOf(project)))
    }

    /** 沒有 `src` 目錄的 project 不造成錯誤。 */
    @Test
    fun `accepts project without sources`() = withFixture { root ->
        assertEquals(emptyList(), jsonFormatBoundaryViolations(root, listOf(root.resolve("empty"))))
    }

    /**
     * 在 project 目錄下寫入原始碼檔案，必要時建立父目錄。
     *
     * @param path 相對於 project 目錄的檔案路徑。
     * @param text 檔案內容。
     */
    private fun File.write(path: String, text: String) {
        resolve(path).apply { parentFile.mkdirs() }.writeText(text)
    }

    /**
     * 建立並在測試完成後刪除暫存目錄。
     *
     * @param block 使用暫存目錄的測試動作。
     */
    private fun withFixture(block: (File) -> Unit) {
        createTempDirectory("mahjongcraft-json-boundary-test").toFile().apply {
            try {
                block(this)
            } finally {
                deleteRecursively()
            }
        }
    }
}
