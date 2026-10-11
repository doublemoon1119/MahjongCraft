package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * 清除本機 target，讓 Settings 回到 core-only 預設。
 *
 * @property rootDirectory repository 根目錄。
 */
abstract class ClearTargetTask : DefaultTask() {
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    /** 移除 local.dev.properties 中的 target property。 */
    @TaskAction
    fun clearTarget() {
        updateLocalProperty(rootDirectory.get().asFile.resolve("local.dev.properties"), null)
        logger.lifecycle("Development target cleared. Reload the Gradle project to use core-only mode.")
    }
}
