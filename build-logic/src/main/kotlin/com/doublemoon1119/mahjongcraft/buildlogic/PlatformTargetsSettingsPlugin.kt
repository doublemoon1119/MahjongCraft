package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.ExtensionAware

/**
 * 在 Settings 階段選擇 core-only 模式，或載入一個以上 catalog 正式平台 target 的模組。
 *
 * 選擇多個 target 時載入它們模組的聯集，例如同一個 Minecraft 版本的兩個載入器。每個模組的 Java 版本與執行環境提供的函式庫
 * 依它所屬的 target 決定，並以 Gradle extra 交給模組的 convention。
 */
class PlatformTargetsSettingsPlugin : Plugin<Settings> {
    /** 解析 target 來源並載入對應平台模組。 */
    override fun apply(settings: Settings) {
        val targets = PlatformTargetCatalog.loadAll(settings.rootDir)
        val selection = PlatformTargetCatalog.resolveSelectedTargetId(
            gradleProperty = settings.providers.gradleProperty(PlatformTargetCatalog.TARGET_PROPERTY_NAME).orNull,
            environmentVariable = settings.providers.environmentVariable(
                PlatformTargetCatalog.TARGET_ENVIRONMENT_NAME,
            ).orNull,
            localProperty = PlatformTargetCatalog.readLocalTarget(settings.rootDir.resolve("local.dev.properties")),
        )
        val selectedTargets = PlatformTargetCatalog.selectTargets(selection, targets)
        val selectedIds = selectedTargets.map(PlatformTarget::id).ifEmpty { listOf(PlatformTargetCatalog.CORE_TARGET_ID) }
        val examplesEnabled = parseExamplesProperty(
            settings.providers.gradleProperty(PlatformTargetCatalog.EXAMPLE_PROPERTY_NAME).orNull,
        )
        if (examplesEnabled && selectedTargets.isEmpty()) {
            throw GradleException("MahjongCraft examples require a platform target; core has no example modules")
        }
        if (examplesEnabled && selectedTargets.all { it.exampleModules.isEmpty() }) {
            throw GradleException("Selected target ${selectedIds.joinToString()} does not declare any example modules")
        }
        // 模組依載入順序對應到包含它的 target；多個 target 共用的模組只載入一次。
        val modules = linkedMapOf<String, MutableList<PlatformTarget>>()
        selectedTargets.forEach { target ->
            (target.modules + if (examplesEnabled) target.exampleModules else emptyList()).forEach { path ->
                modules.getOrPut(path) { mutableListOf() } += target
            }
        }
        val projectPaths = modules.keys.associateWith(::modulePathToProjectPath)
        val duplicateProjectPaths = projectPaths.values.groupingBy(String::lowercase).eachCount().filterValues { it > 1 }.keys
        if (duplicateProjectPaths.isNotEmpty()) {
            throw GradleException(
                "Target ${selectedIds.joinToString()} maps multiple modules to the same Gradle project: $duplicateProjectPaths",
            )
        }
        val extras = (settings.gradle as ExtensionAware).extensions.extraProperties
        extras[SELECTED_TARGET_IDS_EXTRA] = selectedIds
        extras[TARGET_JAVA_TOOLCHAIN_EXTRA] = modules.entries.associate { (path, owners) ->
            projectPaths.getValue(path) to sharedValue(path, owners, "java-toolchain", PlatformTarget::javaToolchain)
        }
        extras[TARGET_JAVA_RELEASE_EXTRA] = modules.entries.associate { (path, owners) ->
            projectPaths.getValue(path) to sharedValue(path, owners, "java-release", PlatformTarget::javaRelease)
        }
        // 多個 target 提供不同函式庫時沒有單一答案，記為 null，由讀取的模組回報。
        extras[TARGET_PROVIDED_VERSIONS_EXTRA] = HashMap<String, Map<String, String>?>().apply {
            modules.forEach { (path, owners) ->
                put(projectPaths.getValue(path), owners.map(PlatformTarget::providedVersions).distinct().singleOrNull())
            }
        }
        projectPaths.forEach { (path, projectPath) ->
            settings.include(projectPath)
            settings.project(projectPath).projectDir = settings.rootDir.resolve(path)
        }
        settings.gradle.rootProject { logger.lifecycle("BUILD LOG: Environment active -> Target: ${selectedIds.joinToString()}") }
    }

    /**
     * 取得一個模組所屬的各 target 必須相同的設定值。
     *
     * @param path 模組路徑，用於錯誤訊息。
     * @param owners 包含這個模組的 target。
     * @param name 設定在 catalog 中的名稱，用於錯誤訊息。
     * @param value 從 target 取得設定值的方式。
     * @return 各 target 共同的設定值。
     * @throws GradleException 這些 target 的設定值不同。
     */
    private fun sharedValue(path: String, owners: List<PlatformTarget>, name: String, value: (PlatformTarget) -> Int): Int {
        val values = owners.associate { it.id to value(it) }
        return values.values.distinct().singleOrNull() ?: throw GradleException(
            "Module $path belongs to selected targets with different $name values: $values; select them separately",
        )
    }

    /** 只接受明確的 true／false，避免範例意外因環境值啟用。 */
    private fun parseExamplesProperty(value: String?): Boolean = when (value) {
        null, "false" -> false
        "true" -> true
        else -> throw GradleException(
            "Invalid -P${PlatformTargetCatalog.EXAMPLE_PROPERTY_NAME}='$value'; expected true or false",
        )
    }

    /** 將 repository-relative module path 映射為穩定 Gradle project path。 */
    private fun modulePathToProjectPath(path: String): String = when {
        // 與正式 loader 保持同層，讓 Loom 共用外掛類別；實體目錄仍集中於 example/platform。
        path.startsWith("example/platform/") ->
            ":example-" + path.removePrefix("example/platform/").replace('/', '_')
        path.startsWith("example/") -> {
            val name = path.removePrefix("example/")
            if (name.isEmpty() || name.contains('/')) {
                throw GradleException("Root example module must be an immediate child of example/: $path")
            }
            ":example:example-$name"
        }
        else -> ":" + path.removePrefix("platform/").replace('/', '_')
    }

    companion object {
        /** Gradle extra 中選擇的 target ID 清單的 key；core 時只有 `core`。 */
        const val SELECTED_TARGET_IDS_EXTRA: String = "mahjongcraft.selectedTargetIds"

        /** Gradle extra 中依 project path 排列的 target toolchain 的 key。 */
        const val TARGET_JAVA_TOOLCHAIN_EXTRA: String = "mahjongcraft.targetJavaToolchain"

        /** Gradle extra 中依 project path 排列的 target release 的 key。 */
        const val TARGET_JAVA_RELEASE_EXTRA: String = "mahjongcraft.targetJavaRelease"

        /** Gradle extra 中依 project path 排列的 target 執行環境提供的函式庫版本的 key。 */
        const val TARGET_PROVIDED_VERSIONS_EXTRA: String = "mahjongcraft.targetProvidedVersions"
    }
}
