package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/** 依 key 字典序重新排列所有 Minecraft 語系檔。 */
abstract class SortMinecraftLangFilesTask : DefaultTask() {
    /** Minecraft 平台語系檔目錄。 */
    @get:Internal
    abstract val langDirectory: DirectoryProperty

    /** 逐檔改寫排序後的內容。 */
    @TaskAction
    fun sort() {
        minecraftLangFiles(langDirectory.get().asFile).forEach { file -> file.writeText(sortedLangFileText(file.readText())) }
    }
}
