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

/** 排除所有 convention 共用的 generated source。 */
private fun Project.configureKtlint() {
    extensions.configure<KtlintExtension> {
        filter { exclude("**/generated/**") }
    }
}
