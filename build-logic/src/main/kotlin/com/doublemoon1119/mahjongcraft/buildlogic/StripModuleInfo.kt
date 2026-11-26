package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 去掉 JAR 中 Java 模組系統的設定（`module-info.class`），讓載入器把它當成一般函式庫。
 *
 * 例如 `kotlinx-datetime` 的模組設定要求名為 `kotlin.stdlib` 的模組，但 Kotlin for Forge 並非以這個名稱提供 Kotlin，
 * 保留設定時 Forge 啟動就會失敗。轉換只改檔案內容，依賴的 Maven 座標不變，jar-in-jar 仍能與其他 mod 協調版本。
 */
abstract class StripModuleInfo : TransformAction<TransformParameters.None> {
    /** 要轉換的 JAR。 */
    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputArtifact: Provider<FileSystemLocation>

    /** 有模組設定時輸出去掉設定的副本，沒有時原樣交出。 */
    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        if (!input.isFile || !hasModuleInfo(input)) {
            outputs.file(input)
            return
        }
        stripModuleInfo(input, outputs.file(input.name))
    }

    companion object {
        /** 標記 JAR 是否已去掉模組設定的屬性。 */
        val MODULE_INFO_STRIPPED: Attribute<Boolean> = Attribute.of("com.doublemoon1119.mahjongcraft.moduleInfoStripped", Boolean::class.javaObjectType)
    }
}

/**
 * 判斷 JAR 是否含有模組設定。
 *
 * @param jar 要檢查的 JAR。
 * @return 根目錄或多版本目錄下有 `module-info.class` 時為 true。
 */
internal fun hasModuleInfo(jar: File): Boolean = ZipFile(jar).use { zip -> zip.entries().asSequence().any { isModuleInfo(it.name) } }

/**
 * 複製 JAR，略過所有模組設定。
 *
 * @param input 原始 JAR。
 * @param output 寫出的副本。
 */
internal fun stripModuleInfo(input: File, output: File) {
    ZipFile(input).use { zip ->
        ZipOutputStream(output.outputStream().buffered()).use { out ->
            zip.entries().asSequence().filterNot { isModuleInfo(it.name) }.forEach { entry ->
                out.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                if (!entry.isDirectory) zip.getInputStream(entry).use { it.copyTo(out) }
                out.closeEntry()
            }
        }
    }
}

/**
 * 判斷 JAR 中的路徑是否為模組設定。
 *
 * @param path JAR 中的路徑。
 * @return 根目錄或 `META-INF/versions/<版本>/` 下的 `module-info.class` 時為 true。
 */
private fun isModuleInfo(path: String): Boolean = path == MODULE_INFO || (path.startsWith("META-INF/versions/") && path.endsWith("/$MODULE_INFO"))

/** 模組設定的檔名。 */
private const val MODULE_INFO = "module-info.class"
