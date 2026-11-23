package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.getByType

/** ModDevGradle 舊版 Forge 外掛的 plugin ID；以原版編譯的模組也用它取得原版 Minecraft。 */
internal const val MODDEV_LEGACY_FORGE_PLUGIN_ID: String = "net.neoforged.moddev.legacyforge"

/** ModDevGradle 準備 Minecraft 的任務共同的類別。 */
private const val NEOFORM_RUNTIME_TASK_CLASS: String = "net.neoforged.nfrtgradle.NeoFormRuntimeTask"

/**
 * 讓 ModDevGradle 準備 Minecraft 的工具改用執行 Gradle 的 Java。
 *
 * 以原版編譯的模組與 Forge 模組都用這些工具，它們預設另找外掛指定版本的 Java 執行；改用目前的 Java 就不需要另外安裝或
 * 下載 JDK（CI 停用自動下載）。外掛不是 build-logic 的依賴，因此以類別名稱與屬性名稱取得它的任務設定。
 */
internal fun Project.useGradleJavaForNeoForm() {
    pluginManager.withPlugin(MODDEV_LEGACY_FORGE_PLUGIN_ID) {
        val pluginClassLoader = plugins.getPlugin(MODDEV_LEGACY_FORGE_PLUGIN_ID).javaClass.classLoader
        val taskType = Class.forName(NEOFORM_RUNTIME_TASK_CLASS, false, pluginClassLoader)
        val javaExecutable = extensions.getByType<JavaToolchainService>()
            .launcherFor { languageVersion.set(JavaLanguageVersion.current()) }
            .map { it.executablePath.asFile.absolutePath }
        tasks.matching(taskType::isInstance).configureEach {
            @Suppress("UNCHECKED_CAST")
            (javaClass.getMethod("getJavaExecutable").invoke(this) as Property<String>).set(javaExecutable)
        }
    }
}
