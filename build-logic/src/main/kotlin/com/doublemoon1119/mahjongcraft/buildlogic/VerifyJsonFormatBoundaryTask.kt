package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * 阻擋 JSON 型別出現在不該知道序列化格式的模組正式程式碼中。
 *
 * JSON 只屬於存檔格式、網路 DTO 與平台轉接層；規則、AI、Flow 的 common／server／client、擴充 API 與內建擴充
 * 只透過 DTO 與 serializer 描述資料，不得依賴 `kotlinx.serialization.json`。
 */
abstract class VerifyJsonFormatBoundaryTask : DefaultTask() {
    /** repository 根目錄。 */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    /** 正式程式碼不得使用 JSON 型別的 project 目錄。 */
    @get:Internal
    abstract val guardedProjectDirectories: ListProperty<File>

    /** 一次列出所有違規的原始碼位置。 */
    @TaskAction
    fun verifyBoundary() {
        val root = rootDirectory.get().asFile
        val violations = jsonFormatBoundaryViolations(root, guardedProjectDirectories.get())
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Found JSON types outside the serialization boundary " +
                    "(only persistence formats, network DTOs and platform adapters may use kotlinx.serialization.json):\n" +
                    violations.joinToString("\n") { "  - $it" },
            )
        }
    }
}

/**
 * 掃描受保護 project 的正式 source set，找出引用 `kotlinx.serialization.json` 的位置。
 *
 * 只檢查名稱以 `Main` 結尾的 source set；測試可以使用 JSON 驗證序列化結果。註解只說明用途、不使用 JSON，因此略過。
 *
 * @param root repository 根目錄，用於產生相對路徑。
 * @param projectDirectories 受保護的 project 目錄。
 * @return 以「相對路徑:行號」標示的違規位置。
 */
internal fun jsonFormatBoundaryViolations(root: File, projectDirectories: List<File>): List<String> = projectDirectories
    .flatMap { projectDir ->
        projectDir.resolve("src").listFiles()
            ?.filter { it.isDirectory && it.name.endsWith("Main") }
            .orEmpty()
    }
    .flatMap { sourceSet -> sourceSet.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
    .flatMap { source ->
        source.readLines().mapIndexedNotNull { index, line ->
            if (JSON_PACKAGE in line && !line.isCommentLine()) "${source.relativeTo(root).invariantSeparatorsPath}:${index + 1}" else null
        }
    }
    .sorted()

/** 判斷此行是否為單行、區塊或 KDoc 註解。 */
private fun String.isCommentLine(): Boolean = trimStart().let { it.startsWith("//") || it.startsWith("/*") || it.startsWith("*") }

/** 不得出現在受保護模組正式程式碼中的套件。 */
private const val JSON_PACKAGE: String = "kotlinx.serialization.json"
