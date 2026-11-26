package com.doublemoon1119.mahjongcraft.buildlogic

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * 寫出 CI 要逐一驗證的目標矩陣。
 *
 * - 沒有指定 [platform] 時是內建的 `core` 加上目錄中的每個平台目標。
 * - 指定 [platform] 時只有那個平台的目標；`core` 表示只有內建的 `core`。每個平台的 CI 流程各自取自己的矩陣。
 * - 檔案是 GitHub Actions 矩陣可直接讀取的 JSON。目錄新增目標時，那個平台的 CI 自動多驗證一格。
 */
abstract class WriteCiTargetMatrixTask : DefaultTask() {
    /** repository 根目錄。 */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    /** `core` 使用的 Java toolchain 版本。 */
    @get:Input
    abstract val coreJavaToolchain: Property<Int>

    /** 只列出這個平台的目標，由 Gradle 屬性 `ciPlatform` 指定；沒有指定時列出全部。 */
    @get:Input
    @get:Optional
    abstract val platform: Property<String>

    /** 寫出的矩陣檔案。 */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        doNotTrackState("Reads the platform target catalog and module directories on every run")
    }

    /** 讀取目標目錄並寫出矩陣。 */
    @TaskAction
    fun write() {
        val targets = PlatformTargetCatalog.loadAll(rootDirectory.get().asFile)
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(ciTargetMatrix(coreJavaToolchain.get(), targets, platform.orNull?.trim()?.takeIf(String::isNotEmpty)))
        logger.lifecycle("Wrote CI target matrix to {}", file)
    }
}

/**
 * CI 目標矩陣的 JSON：`include` 中每個目標有目標 ID、要安裝的 JDK 版本，以及是否一起建置範例模組。
 *
 * @param coreJavaToolchain `core` 使用的 Java toolchain 版本。
 * @param targets 目錄中的平台目標。
 * @param platform 只列出這個平台的目標，`core` 表示只有內建的 `core`；null 時列出 `core` 與全部平台目標。
 * @return 單行 JSON。
 * @throws GradleException 目錄中沒有 [platform] 的目標時拋出。
 */
internal fun ciTargetMatrix(coreJavaToolchain: Int, targets: List<PlatformTarget>, platform: String? = null): String = buildJsonObject {
    val includeCore = platform == null || platform == PlatformTargetCatalog.CORE_TARGET_ID
    val platformTargets = targets.filter { platform == null || it.platform == platform }
    if (!includeCore && platformTargets.isEmpty()) throw GradleException("No platform targets are declared for CI platform '$platform'")
    put(
        "include",
        buildJsonArray {
            if (includeCore) add(ciTarget(PlatformTargetCatalog.CORE_TARGET_ID, listOf(coreJavaToolchain), examples = false))
            platformTargets.forEach { target ->
                val javaToolchains = (listOf(target.javaToolchain) + target.additionalJavaToolchains).distinct()
                add(ciTarget(target.id, javaToolchains, examples = target.exampleModules.isNotEmpty()))
            }
        },
    )
}.toString()

/**
 * 矩陣中的一格。
 *
 * @param target 目標 ID。
 * @param javaToolchains 這個目標要安裝的 JDK 版本：編譯用的 toolchain 在前，接著是建置工具另外需要的版本。
 * @param examples 是否一起建置範例模組。
 * @return 一格的 JSON 物件。
 */
private fun ciTarget(target: String, javaToolchains: List<Int>, examples: Boolean) = buildJsonObject {
    put("target", target)
    put("javaToolchains", buildJsonArray { javaToolchains.forEach { add(JsonPrimitive(it)) } })
    put("examples", examples)
}
