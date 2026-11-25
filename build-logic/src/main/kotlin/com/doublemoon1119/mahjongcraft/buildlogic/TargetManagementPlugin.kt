package com.doublemoon1119.mahjongcraft.buildlogic

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 在 root project 註冊本機 target 管理與查詢任務。
 *
 * 任務需要的路徑與 `-PtoTarget` 都在設定階段交給任務屬性，執行時不讀取 `Task.project`，因此相容於 configuration cache。
 */
class TargetManagementPlugin : Plugin<Project> {
    /** 註冊 target tasks。 */
    override fun apply(project: Project) {
        require(project == project.rootProject) { "mahjongcraft.target-management can only be applied to the root project" }
        val rootDirectory = project.layout.projectDirectory
        project.tasks.register("listPlatformTargets", ListPlatformTargetsTask::class.java) {
            group = "mahjong"
            description = "Lists the built-in core target and all catalog-backed platform targets."
            this.rootDirectory.set(rootDirectory)
        }
        project.tasks.register("writeCiTargetMatrix", WriteCiTargetMatrixTask::class.java) {
            group = "mahjong"
            description = "Writes the targets CI verifies, with their Java toolchains, as a GitHub Actions matrix."
            this.rootDirectory.set(rootDirectory)
            coreJavaToolchain.set(project.provider { project.catalogVersion("core-java-toolchain").toInt() })
            outputFile.set(project.layout.buildDirectory.file("ci/target-matrix.json"))
        }
        project.tasks.register(CI_ARTIFACTS_TASK_NAME, Sync::class.java) {
            group = "mahjong"
            description = "Collects the distributable mods of the selected target into one directory for CI to upload."
            into(project.layout.buildDirectory.dir("ci/artifacts"))
        }
        project.tasks.register("switchTarget", SwitchTargetTask::class.java) {
            group = "mahjong"
            description = "Updates local.dev.properties with a validated MahjongCraft target."
            this.rootDirectory.set(rootDirectory)
            requestedTarget.set(project.providers.gradleProperty("toTarget"))
        }
        project.tasks.register("clearTarget", ClearTargetTask::class.java) {
            group = "mahjong"
            description = "Removes the local MahjongCraft target and restores the core-only default."
            this.rootDirectory.set(rootDirectory)
        }
    }
}

/** 列出正式 target，並支援供 CI 使用的 JSON 輸出。 */
abstract class ListPlatformTargetsTask : DefaultTask() {
    private var outputFormat: String = "text"

    /** repository 根目錄。 */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    /**
     * 設定 `text` 或 `json` 輸出格式。
     *
     * @param value 要使用的輸出格式。
     */
    @Option(option = "format", description = "Output format: text or json.")
    fun setOutputFormat(value: String) {
        outputFormat = value
    }

    /** 讀取 catalog 並輸出 target 清單。 */
    @TaskAction
    fun listTargets() {
        val targets = loadTargets(rootDirectory.get().asFile)
        when (outputFormat.lowercase()) {
            "text" -> {
                logger.lifecycle(PlatformTargetCatalog.CORE_TARGET_ID)
                targets.forEach { target ->
                    logger.lifecycle(target.id)
                    if (target.exampleModules.isNotEmpty()) {
                        logger.lifecycle("  examples: ${target.exampleModules.joinToString()}")
                    }
                }
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
                                    "exampleModules",
                                    buildJsonArray { target.exampleModules.forEach { add(it) } },
                                )
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
    /** repository 根目錄。 */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    /** `-PtoTarget` 指定的 target ID，可以是以逗號分隔的多個 ID。未指定時沒有值。 */
    @get:Internal
    abstract val requestedTarget: Property<String>

    /** 驗證並切換本機 target。 */
    @TaskAction
    fun switchTarget() {
        val root = rootDirectory.get().asFile
        val requested = requestedTarget.orNull
            ?.trim()?.takeIf(String::isNotEmpty)
            ?: throw GradleException("Missing -PtoTarget=<target-id>[,<target-id>...]")
        val selected = PlatformTargetCatalog.selectTargets(requested, loadTargets(root))
        val selection = selected.joinToString(",", transform = PlatformTarget::id).ifEmpty { PlatformTargetCatalog.CORE_TARGET_ID }
        updateLocalProperty(root.resolve("local.dev.properties"), selection)
        logger.lifecycle("Development target switched to $selection. Reload the Gradle project to apply it.")
    }
}

/**
 * 從 [root] 下的 catalog 載入 target。
 *
 * @param root repository 根目錄。
 * @return 已驗證且依 catalog 順序排列的 target。
 */
private fun loadTargets(root: File): List<PlatformTarget> = PlatformTargetCatalog.loadAll(root)

/**
 * 只替換 target property，保留 local file 中其餘內容。
 *
 * @param file 要更新或移除的本機設定檔。
 * @param targetId 要寫入的 target ID。為 null 時移除 target property。
 */
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

/**
 * 收集目前目標所有交付成品的 root 任務名稱。各平台的建置慣例把自己的成品接到這個任務。
 */
const val CI_ARTIFACTS_TASK_NAME: String = "collectCiArtifacts"
