package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.ExtensionAware

/** 在 Settings 階段選擇 core-only 模式或載入單一 catalog 正式平台 target。 */
class PlatformTargetsSettingsPlugin : Plugin<Settings> {
    /** 解析 target 來源並載入對應平台模組。 */
    override fun apply(settings: Settings) {
        val targets = PlatformTargetCatalog.load(
            settings.rootDir.resolve("gradle/platform-targets.toml"),
            settings.rootDir,
        )
        val selectedId = PlatformTargetCatalog.resolveSelectedTargetId(
            gradleProperty = settings.providers.gradleProperty(PlatformTargetCatalog.TARGET_PROPERTY_NAME).orNull,
            environmentVariable = settings.providers.environmentVariable(
                PlatformTargetCatalog.TARGET_ENVIRONMENT_NAME,
            ).orNull,
            localProperty = PlatformTargetCatalog.readLocalTarget(settings.rootDir.resolve("local.dev.properties")),
        )
        val selectedTarget = when (selectedId) {
            PlatformTargetCatalog.CORE_TARGET_ID -> null
            else -> targets.singleOrNull { it.id == selectedId }
                ?: throw GradleException(
                    "Unknown MahjongCraft target '$selectedId'. Available targets: " +
                        (listOf(PlatformTargetCatalog.CORE_TARGET_ID) + targets.map(PlatformTarget::id)).joinToString(),
                )
        }
        val examplesEnabled = parseExamplesProperty(
            settings.providers.gradleProperty(PlatformTargetCatalog.EXAMPLE_PROPERTY_NAME).orNull,
        )
        if (examplesEnabled && selectedTarget == null) {
            throw GradleException("MahjongCraft examples require a platform target; core has no example modules")
        }
        if (examplesEnabled && selectedTarget?.exampleModules.isNullOrEmpty()) {
            throw GradleException("Target '$selectedId' does not declare any example modules")
        }
        val extras = (settings.gradle as ExtensionAware).extensions.extraProperties
        extras[SELECTED_TARGET_ID_EXTRA] = selectedId
        extras[TARGET_JAVA_TOOLCHAIN_EXTRA] = selectedTarget?.javaToolchain ?: 0
        extras[TARGET_JAVA_RELEASE_EXTRA] = selectedTarget?.javaRelease ?: 0
        val modules = selectedTarget?.let { target ->
            target.modules + if (examplesEnabled) target.exampleModules else emptyList()
        }.orEmpty()
        val projectPaths = modules.map { modulePathToProjectPath(it) }
        val duplicateProjectPaths = projectPaths.groupingBy(String::lowercase).eachCount().filterValues { it > 1 }.keys
        if (duplicateProjectPaths.isNotEmpty()) {
            throw GradleException(
                "Target '$selectedId' maps multiple modules to the same Gradle project: $duplicateProjectPaths",
            )
        }
        modules.zip(projectPaths).forEach { (path, projectPath) ->
            settings.include(projectPath)
            settings.project(projectPath).projectDir = settings.rootDir.resolve(path)
        }
        if (selectedTarget == null) {
            settings.gradle.rootProject { logger.lifecycle("BUILD LOG: Environment active -> Target: core") }
        } else {
            settings.gradle.rootProject { logger.lifecycle("BUILD LOG: Environment active -> Target: ${selectedTarget.id}") }
        }
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
        /** Gradle extra 中的 target ID key。 */
        const val SELECTED_TARGET_ID_EXTRA: String = "mahjongcraft.selectedTargetId"

        /** Gradle extra 中的 target toolchain key。 */
        const val TARGET_JAVA_TOOLCHAIN_EXTRA: String = "mahjongcraft.targetJavaToolchain"

        /** Gradle extra 中的 target release key。 */
        const val TARGET_JAVA_RELEASE_EXTRA: String = "mahjongcraft.targetJavaRelease"
    }
}
