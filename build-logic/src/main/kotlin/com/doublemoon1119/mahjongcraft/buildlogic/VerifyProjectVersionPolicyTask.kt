package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/** 驗證所有載入的 project 都符合 repository 的版本政策，包括 root 與 testing 專案的固定版本。 */
abstract class VerifyProjectVersionPolicyTask : DefaultTask() {
    /** repository 根目錄。 */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    /** 依 project path 索引的實際版本。 */
    @get:Internal
    abstract val projectVersions: MapProperty<String, String>

    /** 依 project path 索引的 project 目錄。 */
    @get:Internal
    abstract val projectDirectories: MapProperty<String, File>

    /** version catalog 中各 release train 的版本，key 為 catalog 的版本名稱。 */
    @get:Internal
    abstract val releaseTrainVersions: MapProperty<String, String>

    /** 比對 project path、平台目錄與 version catalog。 */
    @TaskAction
    fun verifyVersions() {
        val versions = releaseTrainVersions.get()
        val minecraftDirectory = rootDirectory.get().asFile.resolve("platform/minecraft").toPath()
        val directories = projectDirectories.get()
        val violations = projectVersions.get().mapNotNull { (path, actual) ->
            val expected = when {
                path == ":" -> "0.0.0-dev"
                path == ":mahjong-logic" -> versions.getValue("logic-version")
                path == ":mahjong-ai" -> versions.getValue("ai-version")
                path == ":mahjong-extension-api" -> versions.getValue("extension-api-version")
                path == ":mahjong-bundled-extensions" -> versions.getValue("bundled-extensions-version")
                path == ":mahjong-flow" || path.startsWith(":mahjong-flow:") -> versions.getValue("flow-version")
                path == ":testing" || path.startsWith(":testing:") -> "0.0.0-dev"
                directories.getValue(path).toPath().startsWith(minecraftDirectory) -> versions.getValue("minecraft-mod-version")
                else -> return@mapNotNull "$path has no version policy"
            }
            if (actual == expected) null else "$path: expected $expected, found $actual"
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Project version policy violations:\n" + violations.joinToString("\n") { "  - $it" })
        }
    }
}
