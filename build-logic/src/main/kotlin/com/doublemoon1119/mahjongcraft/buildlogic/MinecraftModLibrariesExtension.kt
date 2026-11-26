package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.exclude

/**
 * MahjongCraft mod 執行時需要、由載入器模組一起交付的函式庫：本專案的模組與外部函式庫。
 *
 * 各載入器以自己的方式附帶它們，例如 Fabric 的 `include` 或 Forge 的 jar-in-jar，清單只在這裡維護。載入器模組、版本 mod 模組與
 * 它們的 debug 模組把整份清單加進 `implementation`，因為它們直接使用這些模組的公開型別：本專案的模組彼此多以 `implementation`
 * 依賴，不會把型別傳給下游。
 *
 * @property project 套用載入器或版本 mod convention 的模組。
 */
abstract class MinecraftModLibrariesExtension(private val project: Project) {
    /**
     * 把清單中的函式庫加進 [configurationName]。
     *
     * @param configurationName 要加入的設定名稱。
     * @param kinds 要加入的函式庫種類。
     * @param excludeRuntimeProvided 是否排除執行環境已提供的函式庫，例如載入器附帶的 Kotlin 標準函式庫。附帶函式庫的設定
     * 使用，編譯用的設定不使用。
     */
    fun addTo(
        configurationName: String,
        kinds: Set<MinecraftModLibraryKind> = MinecraftModLibraryKind.entries.toSet(),
        excludeRuntimeProvided: Boolean = false,
    ) {
        val exclusions = if (excludeRuntimeProvided) runtimeProvidedModules(project.targetProvidedVersions().keys) else emptyList()
        val configure: ModuleDependency.() -> Unit = { exclusions.forEach { exclude(group = it.group, module = it.module) } }
        if (MinecraftModLibraryKind.PROJECTS in kinds) {
            PROJECT_LIBRARIES.forEach { path ->
                (project.dependencies.add(configurationName, project.dependencies.project(mapOf("path" to path))) as ModuleDependency).configure()
            }
        }
        if (MinecraftModLibraryKind.EXTERNAL in kinds) {
            val catalog = project.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
            EXTERNAL_LIBRARY_ALIASES.forEach { alias ->
                val library = catalog.findLibrary(alias).orElseThrow { GradleException("Missing version catalog library: $alias") }
                project.dependencies.addProvider<MinimalExternalModuleDependency, ExternalModuleDependency>(configurationName, library) { configure() }
            }
        }
    }

    private companion object {
        /** 本專案的模組。 */
        val PROJECT_LIBRARIES: List<String> = listOf(
            ":minecraft_common",
            ":mahjong-logic",
            ":mahjong-ai",
            ":mahjong-flow:mahjong-flow-common",
            ":mahjong-flow:mahjong-flow-client",
            ":mahjong-flow:mahjong-flow-server",
            ":mahjong-flow:mahjong-flow-network-dto",
            ":mahjong-flow:mahjong-flow-persistence-format",
            ":mahjong-extension-api",
            ":mahjong-bundled-extensions",
        )

        /**
         * 外部函式庫在 `libs` version catalog 中的名稱。
         *
         * Kotlin 多平台函式庫使用 `-jvm` 座標：有的打包方式無法辨認從通用座標轉到的 JVM 版本。
         */
        val EXTERNAL_LIBRARY_ALIASES: List<String> = listOf(
            "jvm-koin-core",
            "jvm-ktoml-core",
            "jvm-kotlinx-datetime",
            "exposed-core",
            "exposed-jdbc",
            "sqlite-jdbc",
        )
    }
}

/**
 * 執行環境已提供、mod 不附帶的一個模組。
 *
 * @property group Maven group。
 * @property module Maven artifact。null 代表整個 group。
 */
internal data class RuntimeProvidedModule(val group: String, val module: String? = null)

/**
 * 依 target 提供的函式庫名稱，列出 mod 不附帶的模組。Minecraft 本身附帶的 SLF4J 一律列入。
 *
 * @param providedNames target 在 `provided-versions` 列出的名稱。
 * @return 要從附帶函式庫中排除的模組。
 * @throws GradleException 名稱沒有對應的模組。
 */
internal fun runtimeProvidedModules(providedNames: Set<String>): List<RuntimeProvidedModule> = MINECRAFT_PROVIDED_MODULES +
    providedNames.sorted().flatMap { name ->
        PROVIDED_LIBRARY_MODULES[name] ?: throw GradleException("No modules are known for the provided library '$name'")
    }

/** Minecraft 本身附帶的模組。 */
private val MINECRAFT_PROVIDED_MODULES: List<RuntimeProvidedModule> = listOf(RuntimeProvidedModule("org.slf4j"))

/** `provided-versions` 的名稱對應的模組，名稱與 `libs` version catalog 的版本名稱相同。 */
private val PROVIDED_LIBRARY_MODULES: Map<String, List<RuntimeProvidedModule>> = mapOf(
    KOTLIN_PROVIDED_VERSION_NAME to listOf(RuntimeProvidedModule("org.jetbrains.kotlin")),
    "kotlinx-coroutines" to listOf("kotlinx-coroutines-core", "kotlinx-coroutines-core-jvm").map { RuntimeProvidedModule("org.jetbrains.kotlinx", it) },
    "kotlinx-serialization" to listOf(
        "kotlinx-serialization-core",
        "kotlinx-serialization-core-jvm",
        "kotlinx-serialization-json",
        "kotlinx-serialization-json-jvm",
    ).map { RuntimeProvidedModule("org.jetbrains.kotlinx", it) },
)
