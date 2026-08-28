package com.doublemoon1119.mahjongcraft.buildlogic

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 在 root project 註冊本機 target 管理與查詢任務。 */
class TargetManagementPlugin : Plugin<Project> {
    /** 註冊 target tasks。 */
    override fun apply(project: Project) {
        require(project == project.rootProject) { "mahjongcraft.target-management can only be applied to the root project" }
        project.tasks.register("listPlatformTargets", ListPlatformTargetsTask::class.java) {
            group = "mahjong"
            description = "Lists the built-in core target and all catalog-backed platform targets."
        }
        project.tasks.register("switchTarget", SwitchTargetTask::class.java) {
            group = "mahjong"
            description = "Updates local.dev.properties with a validated MahjongCraft target."
        }
        project.tasks.register("clearTarget", ClearTargetTask::class.java) {
            group = "mahjong"
            description = "Removes the local MahjongCraft target and restores the core-only default."
        }
    }
}

/** 列出正式 target，並支援供 CI 使用的 JSON 輸出。 */
abstract class ListPlatformTargetsTask : DefaultTask() {
    private var outputFormat: String = "text"

    /** 設定 `text` 或 `json` 輸出格式。 */
    @Option(option = "format", description = "Output format: text or json.")
    fun setOutputFormat(value: String) {
        outputFormat = value
    }

    /** 讀取 catalog 並輸出 target 清單。 */
    @TaskAction
    fun listTargets() {
        val targets = loadTargets(project)
        when (outputFormat.lowercase()) {
            "text" -> {
                logger.lifecycle(PlatformTargetCatalog.CORE_TARGET_ID)
                targets.forEach { logger.lifecycle(it.id) }
            }
            "json" -> logger.lifecycle(
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", PlatformTargetCatalog.CORE_TARGET_ID)
                            put("platform", PlatformTargetCatalog.CORE_TARGET_ID)
                        },
                    )
                    targets.forEach { target ->
                        add(
                            buildJsonObject {
                                put("id", target.id)
                                put("platform", target.platform)
                                put("javaToolchain", target.javaToolchain)
                                put("javaRelease", target.javaRelease)
                                put(
                                    "attributes",
                                    buildJsonObject {
                                        target.attributes.toSortedMap().forEach { (key, value) -> put(key, value) }
                                    },
                                )
                            },
                        )
                    }
                }.toString(),
            )
            else -> throw GradleException("Unsupported target list format '$outputFormat'; use text or json")
        }
    }
}

/** 將合法 target 寫入 local.dev.properties。 */
abstract class SwitchTargetTask : DefaultTask() {
    /** 驗證並切換本機 target。 */
    @TaskAction
    fun switchTarget() {
        val targetId = project.providers.gradleProperty("toTarget").orNull
            ?.trim()?.takeIf(String::isNotEmpty)
            ?: throw GradleException("Missing -PtoTarget=<target-id>")
        val validIds = loadTargets(project).mapTo(mutableSetOf(), PlatformTarget::id) + PlatformTargetCatalog.CORE_TARGET_ID
        if (targetId !in validIds) throw GradleException("Unknown target '$targetId'. Available targets: ${validIds.sorted()}")
        updateLocalProperty(project.rootDir.resolve("local.dev.properties"), targetId)
        logger.lifecycle("Development target switched to $targetId. Reload the Gradle project to apply it.")
    }
}

/** 清除本機 target，讓 Settings 回到 core-only 預設。 */
abstract class ClearTargetTask : DefaultTask() {
    /** 移除 local.dev.properties 中的 target property。 */
    @TaskAction
    fun clearTarget() {
        updateLocalProperty(project.rootDir.resolve("local.dev.properties"), null)
        logger.lifecycle("Development target cleared. Reload the Gradle project to use core-only mode.")
    }
}

/** 從 root catalog 載入 target。 */
private fun loadTargets(project: Project): List<PlatformTarget> = PlatformTargetCatalog.load(
    project.rootDir.resolve("gradle/platform-targets.toml"),
    project.rootDir,
)

/** 只替換 target property，保留 local file 中其餘內容。 */
internal fun updateLocalProperty(file: File, targetId: String?) {
    val originalLines = if (file.isFile) file.readLines() else emptyList()
    val propertyPrefix = "${PlatformTargetCatalog.TARGET_PROPERTY_NAME}="
    val retainedLines = originalLines.filterNot { it.trimStart().startsWith(propertyPrefix) }.toMutableList()
    if (targetId != null) retainedLines += propertyPrefix + targetId
    if (retainedLines.isEmpty()) {
        Files.deleteIfExists(file.toPath())
        return
    }
    val temporaryFile = file.resolveSibling("${file.name}.tmp")
    temporaryFile.writeText(retainedLines.joinToString(System.lineSeparator(), postfix = System.lineSeparator()))
    try {
        Files.move(
            temporaryFile.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
        Files.move(temporaryFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
