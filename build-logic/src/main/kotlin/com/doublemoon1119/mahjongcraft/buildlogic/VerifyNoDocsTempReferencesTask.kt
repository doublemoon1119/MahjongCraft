package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/** 阻擋原始碼註解引用不進版控的 `docs/temp` 檔案。 */
abstract class VerifyNoDocsTempReferencesTask : DefaultTask() {
    /** repository 根目錄。 */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    /** 要掃描 `src` 的所有 project 目錄。 */
    @get:Internal
    abstract val projectDirectories: ListProperty<File>

    /** 掃描 Kotlin 原始碼並回報引用。 */
    @TaskAction
    fun verifyReferences() {
        val root = rootDirectory.get().asFile
        val docsTemp = root.resolve("docs/temp")
        if (!docsTemp.exists()) return
        val gitOutput = ProcessBuilder("git", "ls-files", "--others", "--ignored", "--exclude-standard", "--", "docs/temp")
            .directory(root)
            .redirectErrorStream(true)
            .start()
            .let { process ->
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                output
            }
        val ignoredFileNames = gitOutput.lineSequence()
            .map { File(it.trim()).name }
            .filter(String::isNotBlank)
            .toSet()
        if (ignoredFileNames.isEmpty()) return
        val violations = mutableListOf<String>()
        projectDirectories.get().forEach { projectDir ->
            projectDir.resolve("src").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { source ->
                    val text = source.readText()
                    ignoredFileNames.filter(text::contains).forEach { name ->
                        violations += "${source.relativeTo(root)} references docs/temp file: $name"
                    }
                }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Found references to gitignored docs/temp files in source comments " +
                    "(move the explanation into the comment itself instead of citing the file):\n" +
                    violations.joinToString("\n") { "  - $it" },
            )
        }
    }
}
