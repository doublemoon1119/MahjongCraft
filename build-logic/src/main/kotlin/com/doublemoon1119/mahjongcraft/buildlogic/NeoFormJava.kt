package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.getByType

/** ModDevGradle 舊版 Forge 外掛的 plugin ID。以原版編譯的模組也用它取得原版 Minecraft。 */
internal const val MODDEV_LEGACY_FORGE_PLUGIN_ID: String = "net.neoforged.moddev.legacyforge"

/** ModDevGradle 準備 Minecraft 的任務共同的類別。 */
private const val NEOFORM_RUNTIME_TASK_CLASS: String = "net.neoforged.nfrtgradle.NeoFormRuntimeTask"

/** ModDevGradle 準備 Minecraft 的工具本身需要的最低 Java 版本。 */
private const val NEOFORM_RUNTIME_MINIMUM_JAVA: Int = 21

/**
 * 讓 ModDevGradle 準備 Minecraft 的工具使用固定的 Java 工具鏈：目前 target 的 Java 與工具最低需求中較新的版本。
 *
 * 為什麼要固定：
 *
 * - 這些工具會反編譯再重新編譯 Minecraft，重新編譯用的是執行工具的 Java。
 * - 編譯器自動產生的方法（例如 lambda）名稱裡的編號依 Java 版本而不同。較新的 Java 改為每個方法各自編號，與原版不同，
 *   開發執行時修改這類方法的其他模組（例如 Embeddium）就找不到它們。
 * - 執行 Gradle 的 Java 可能比 target 新很多，因此不使用它。
 *
 * 其他須知：
 *
 * - 工具的快取不區分 Java 版本，換版本後要清除快取才會重新編譯。
 * - CI 停用自動下載，只安裝 target 在目錄中宣告的 JDK。Java 低於工具最低需求的 target 要在 `additional-java-toolchains`
 *   宣告工具使用的版本。
 * - 外掛不是 build-logic 的依賴，因此以類別名稱與屬性名稱取得它的任務設定。
 */
internal fun Project.useTargetJavaForNeoForm() {
    pluginManager.withPlugin(MODDEV_LEGACY_FORGE_PLUGIN_ID) {
        val pluginClassLoader = plugins.getPlugin(MODDEV_LEGACY_FORGE_PLUGIN_ID).javaClass.classLoader
        val taskType = Class.forName(NEOFORM_RUNTIME_TASK_CLASS, false, pluginClassLoader)
        val javaExecutable = extensions.getByType<JavaToolchainService>()
            .launcherFor { languageVersion.set(JavaLanguageVersion.of(maxOf(targetJavaToolchain(), NEOFORM_RUNTIME_MINIMUM_JAVA))) }
            .map { it.executablePath.asFile.absolutePath }
        tasks.matching(taskType::isInstance).configureEach {
            @Suppress("UNCHECKED_CAST")
            (javaClass.getMethod("getJavaExecutable").invoke(this) as Property<String>).set(javaExecutable)
        }
    }
}
