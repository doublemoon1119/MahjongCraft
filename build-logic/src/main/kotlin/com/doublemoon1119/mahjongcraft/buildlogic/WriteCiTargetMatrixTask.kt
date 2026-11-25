package com.doublemoon1119.mahjongcraft.buildlogic

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * 寫出 CI 要逐一驗證的目標矩陣：內建的 `core` 加上目錄中的每個平台目標。
 *
 * 檔案是 GitHub Actions 矩陣可直接讀取的 JSON。目錄新增目標時，CI 自動多驗證一格。
 */
abstract class WriteCiTargetMatrixTask : DefaultTask() {
    /** repository 根目錄。 */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    /** `core` 使用的 Java toolchain 版本。 */
    @get:Input
    abstract val coreJavaToolchain: Property<Int>

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
        file.writeText(ciTargetMatrix(coreJavaToolchain.get(), targets))
        logger.lifecycle("Wrote CI target matrix to {}", file)
    }
}

/**
 * CI 目標矩陣的 JSON：`include` 中每個目標有目標 ID、Java toolchain 版本，以及是否一起建置範例模組。
 *
 * @param coreJavaToolchain `core` 使用的 Java toolchain 版本。
 * @param targets 目錄中的平台目標。
 * @return 單行 JSON。
 */
internal fun ciTargetMatrix(coreJavaToolchain: Int, targets: List<PlatformTarget>): String = buildJsonObject {
    put(
        "include",
        buildJsonArray {
            add(ciTarget(PlatformTargetCatalog.CORE_TARGET_ID, coreJavaToolchain, examples = false))
            targets.forEach { target -> add(ciTarget(target.id, target.javaToolchain, examples = target.exampleModules.isNotEmpty())) }
        },
    )
}.toString()

/**
 * 矩陣中的一格。
 *
 * @param target 目標 ID。
 * @param javaToolchain 這個目標的 Java toolchain 版本。
 * @param examples 是否一起建置範例模組。
 * @return 一格的 JSON 物件。
 */
private fun ciTarget(target: String, javaToolchain: Int, examples: Boolean) = buildJsonObject {
    put("target", target)
    put("javaToolchain", javaToolchain)
    put("examples", examples)
}
