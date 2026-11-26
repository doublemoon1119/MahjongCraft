package com.doublemoon1119.mahjongcraft.buildlogic

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證載入器打包時處理 Java 模組設定與同名套件的輔助函式。 */
class JarModuleLayoutTest {
    /** 去掉根目錄與多版本目錄的模組設定，其餘檔案原樣保留。 */
    @Test
    fun `strips every module info and keeps other entries`() = withDirectory { directory ->
        val input = directory.resolve("library.jar").also {
            writeJar(it, "module-info.class", "META-INF/versions/9/module-info.class", "kotlinx/datetime/LocalDate.class")
        }
        val output = directory.resolve("stripped.jar")

        assertTrue(hasModuleInfo(input))
        stripModuleInfo(input, output)

        assertFalse(hasModuleInfo(output))
        assertEquals(listOf("kotlinx/datetime/LocalDate.class"), entryNames(output))
    }

    /** 同一個套件出現在 mod 與附帶的 JAR 時列出兩邊。只在 META-INF 的類別不算。 */
    @Test
    fun `reports packages shared between the mod and embedded jars`() = withDirectory { directory ->
        val modJar = directory.resolve("mod.jar")
        ZipOutputStream(modJar.outputStream()).use { out ->
            out.putEntry("org/koin/plugin/hints/ModHints.class", ByteArray(0))
            out.putEntry("com/example/mod/Mod.class", ByteArray(0))
            out.putEntry("META-INF/jarjar/common.jar", jarBytes("org/koin/plugin/hints/CommonHints.class", "com/example/common/Common.class"))
            out.putEntry("META-INF/jarjar/datetime.jar", jarBytes("META-INF/versions/9/module-info.class", "kotlinx/datetime/LocalDate.class"))
        }

        assertEquals(mapOf("org.koin.plugin.hints" to listOf("<mod>", "common.jar")), findSplitPackages(modJar))
    }

    /** 沒有重複套件時為空。 */
    @Test
    fun `accepts jars without shared packages`() = withDirectory { directory ->
        val modJar = directory.resolve("mod.jar")
        ZipOutputStream(modJar.outputStream()).use { out ->
            out.putEntry("com/example/mod/Mod.class", ByteArray(0))
            out.putEntry("META-INF/jarjar/common.jar", jarBytes("com/example/common/Common.class"))
        }

        assertEquals(emptyMap(), findSplitPackages(modJar))
    }

    /**
     * 在暫時資料夾中執行，結束後刪除。
     *
     * @param block 使用資料夾的測試內容。
     */
    private fun withDirectory(block: (File) -> Unit) {
        val directory = createTempDirectory("jar-module-layout").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    /**
     * 寫出只含指定空白檔案的 JAR。
     *
     * @param file 輸出的 JAR。
     * @param names JAR 中的路徑。
     */
    private fun writeJar(file: File, vararg names: String) {
        file.writeBytes(jarBytes(*names))
    }

    /**
     * 建立只含指定空白檔案的 JAR 內容。
     *
     * @param names JAR 中的路徑。
     * @return JAR 的位元組。
     */
    private fun jarBytes(vararg names: String): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { out -> names.forEach { out.putEntry(it, ByteArray(0)) } }
    }.toByteArray()

    /**
     * 寫入一個 JAR 項目。
     *
     * @param name JAR 中的路徑。
     * @param content 項目內容。
     */
    private fun ZipOutputStream.putEntry(name: String, content: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(content)
        closeEntry()
    }

    /**
     * 讀出 JAR 中所有檔案的路徑。
     *
     * @param jar 要讀的 JAR。
     * @return 依順序排列的路徑。
     */
    private fun entryNames(jar: File): List<String> = ZipFile(jar).use { zip -> zip.entries().asSequence().map { it.name }.toList() }
}
