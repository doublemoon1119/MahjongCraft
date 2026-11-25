package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * 開發用戶端啟動前，把開發用的光影包放進執行目錄並設為使用中。
 *
 * - 複製到 `shaderpacks`，在 Iris 的設定檔指定這個光影包並開啟光影。
 * - Iris 的其他設定保留上次的值。
 * - 光影包只在開發執行時使用，從 Gradle 的快取複製，不放進版本庫，也不打包。
 *
 * @property runDirectory 用戶端的執行目錄。
 * @property shaderPack 光影包檔案，只有一個。
 */
@DisableCachingByDefault(because = "Writes into a local run directory")
abstract class PrepareDevShaderPackTask : DefaultTask() {
    @get:Internal
    abstract val runDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val shaderPack: ConfigurableFileCollection

    /** 複製光影包並寫入 Iris 的設定。 */
    @TaskAction
    fun prepare() {
        val pack = shaderPack.singleFile
        val directory = runDirectory.get().asFile
        pack.copyTo(directory.resolve("shaderpacks/${pack.name}"), overwrite = true)
        val settings = directory.resolve(IRIS_SETTINGS).apply { parentFile.mkdirs() }
        val values = mapOf("shaderPack" to pack.name, "enableShaders" to "true")
        val kept = if (settings.isFile) settings.readLines().filter { line -> line.substringBefore('=').trim() !in values } else emptyList()
        settings.writeText((kept + values.map { (key, value) -> "$key=$value" }).joinToString(separator = "\n", postfix = "\n"))
    }

    /** Iris 設定檔的位置。 */
    companion object {
        /** Iris 設定檔相對於執行目錄的路徑。 */
        const val IRIS_SETTINGS: String = "config/iris.properties"
    }
}
