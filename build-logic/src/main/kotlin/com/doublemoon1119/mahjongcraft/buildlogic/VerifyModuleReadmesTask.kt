package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/** 驗證所有模組與平台索引都具備人工維護的 `README.md`。 */
abstract class VerifyModuleReadmesTask : DefaultTask() {
    /** repository 根目錄。 */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    /** 供驗證使用的 README 集合。 */
    @get:Internal
    abstract val expectedReadmes: SetProperty<File>

    /** 一次列出所有缺少的 README。 */
    @TaskAction
    fun verifyReadmes() {
        val root = rootDirectory.get().asFile
        val missing = expectedReadmes.get().filterNot(::hasExactReadmeName)
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing required README.md files:\n" +
                    missing.joinToString("\n") { "  - ${it.relativeTo(root).invariantSeparatorsPath}" },
            )
        }
    }
}

/**
 * 即使在大小寫不敏感的檔案系統，也只接受精確的 `README.md` 名稱。
 *
 * @param readme 預期的 README.md 檔案路徑。
 * @return 父目錄中存在精確檔名的檔案時為 true。
 */
private fun hasExactReadmeName(readme: File): Boolean = readme.parentFile
    ?.listFiles()
    ?.any { it.isFile && it.name == "README.md" }
    ?: false
