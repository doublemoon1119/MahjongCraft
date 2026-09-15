package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/** 套用 MahjongCraft KMP module 的核心 Java baseline 與格式設定。 */
class MahjongCraftKotlinMultiplatformPlugin : Plugin<Project> {
    /** 套用 KMP、base、ktlint 與核心 JVM 編譯政策。 */
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("mahjongcraft.base")
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("org.jlleitschuh.gradle.ktlint")
        configureKtlint()
        configureMultiplatformJvm(
            catalogVersion("core-java-toolchain").toInt(),
            catalogVersion("core-java-release").toInt(),
            overrideDefaults = false,
        )
    }
}

/** 套用 MahjongCraft Kotlin JVM module 的核心 Java baseline 與格式設定。 */
class MahjongCraftKotlinJvmPlugin : Plugin<Project> {
    /** 套用 Kotlin JVM、base、ktlint 與核心 JVM 編譯政策。 */
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("mahjongcraft.base")
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("org.jlleitschuh.gradle.ktlint")
        configureKtlint()
        configureKotlinJvm(
            catalogVersion("core-java-toolchain").toInt(),
            catalogVersion("core-java-release").toInt(),
            overrideDefaults = false,
        )
    }
}

/**
 * 排除所有 convention 共用的 generated source，並掛上專案自訂 rule set。
 *
 * `:ktlint-rules` 自己不掛——它就是規則的來源，掛上去會讓它的 ktlint 任務依賴自身產物。
 */
private fun Project.configureKtlint() {
    extensions.configure<KtlintExtension> {
        filter { exclude("**/generated/**") }
    }
    if (path != CUSTOM_RULE_SET_PROJECT_PATH) {
        dependencies.add("ktlintRuleset", dependencies.project(mapOf("path" to CUSTOM_RULE_SET_PROJECT_PATH)))
    }
}

/** 提供專案自訂 ktlint rule set 的模組路徑。 */
private const val CUSTOM_RULE_SET_PROJECT_PATH: String = ":ktlint-rules"
