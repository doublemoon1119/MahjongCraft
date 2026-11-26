package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/** 依 key 字典序重新排列所有 Minecraft 語系檔。 */
abstract class SortMinecraftLangFilesTask : DefaultTask() {
    /** MahjongCraft 本體與開發專用模組的語系檔目錄。 */
    @get:Internal
    abstract val langDirectories: ListProperty<File>

    /** 逐檔改寫排序後的內容。 */
    @TaskAction
    fun sort() {
        langDirectories.get().flatMap(::minecraftLangFiles).forEach { file -> file.writeText(sortedLangFileText(file.readText())) }
    }
}
