package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/** 驗證所有 Minecraft 語系檔已依 key 字典序排列。 */
abstract class VerifyMinecraftLangFileKeyOrderTask : DefaultTask() {
    /** repository 根目錄。 */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    /** Minecraft 平台語系檔目錄。 */
    @get:Internal
    abstract val langDirectory: DirectoryProperty

    /** 逐檔比對排序前後內容，回報未排序的檔案。 */
    @TaskAction
    fun verifyOrder() {
        val root = rootDirectory.get().asFile
        val violations = minecraftLangFiles(langDirectory.get().asFile).filterNot { isLangFileSorted(it.readText()) }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Language files are not sorted by translation key (run ./gradlew sortMinecraftLangFiles to fix):\n" +
                    violations.joinToString("\n") { "  - ${it.relativeTo(root).invariantSeparatorsPath}" },
            )
        }
    }
}
