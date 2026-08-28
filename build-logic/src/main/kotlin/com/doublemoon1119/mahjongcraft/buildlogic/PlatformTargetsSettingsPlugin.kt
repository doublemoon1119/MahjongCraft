package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.ExtensionAware

/** 在 Settings 階段選擇並載入單一正式平台 target。 */
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
        val extras = (settings.gradle as ExtensionAware).extensions.extraProperties
        extras[SELECTED_TARGET_ID_EXTRA] = selectedId
        extras[TARGET_JAVA_TOOLCHAIN_EXTRA] = selectedTarget?.javaToolchain ?: 0
        extras[TARGET_JAVA_RELEASE_EXTRA] = selectedTarget?.javaRelease ?: 0
        selectedTarget?.modules?.forEach { path ->
            val projectPath = ":" + path.removePrefix("platform/").replace('/', '_')
            settings.include(projectPath)
            settings.project(projectPath).projectDir = settings.rootDir.resolve(path)
        }
        if (selectedTarget == null) {
            settings.gradle.rootProject { logger.lifecycle("BUILD LOG: Environment active -> Target: core") }
        } else {
            settings.gradle.rootProject { logger.lifecycle("BUILD LOG: Environment active -> Target: ${selectedTarget.id}") }
        }
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
