package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * 從 root version catalog 取得指定 version alias。
 *
 * @param alias 要查詢的 version catalog alias。
 */
internal fun Project.catalogVersion(alias: String): String = extensions.getByType<VersionCatalogsExtension>()
    .named("libs")
    .findVersion(alias)
    .orElseThrow { IllegalStateException("Missing version catalog alias: $alias") }
    .requiredVersion

/**
 * 將共用 JVM 編譯政策套用到 KMP 專案。
 *
 * @param toolchain 編譯與測試使用的 JDK toolchain 版本。
 * @param release 編譯輸出的 JVM bytecode release。
 * @param overrideDefaults 是否直接設定 release 與 jvmTarget；false 時以 convention 提供。
 */
internal fun Project.configureMultiplatformJvm(toolchain: Int, release: Int, overrideDefaults: Boolean) {
    extensions.configure<KotlinMultiplatformExtension> {
        jvmToolchain(toolchain)
    }
    configureCompilerTasks(toolchain, release, overrideDefaults)
}

/**
 * 將共用 JVM 編譯政策套用到 Kotlin JVM 專案。
 *
 * @param toolchain 編譯與測試使用的 JDK toolchain 版本。
 * @param release 編譯輸出的 JVM bytecode release。
 * @param overrideDefaults 是否直接設定 release 與 jvmTarget；false 時以 convention 提供。
 */
internal fun Project.configureKotlinJvm(toolchain: Int, release: Int, overrideDefaults: Boolean) {
    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(toolchain)
    }
    configureCompilerTasks(toolchain, release, overrideDefaults)
}

/**
 * 設定 Java/Kotlin bytecode release 與測試 launcher。
 *
 * @param toolchain 測試 launcher 使用的 JDK toolchain 版本。
 * @param release Java 與 Kotlin 編譯輸出的 JVM bytecode release。
 * @param overrideDefaults 是否直接設定 release 與 jvmTarget；false 時以 convention 提供。
 */
private fun Project.configureCompilerTasks(toolchain: Int, release: Int, overrideDefaults: Boolean) {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        if (overrideDefaults) options.release.set(release) else options.release.convention(release)
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            val target = JvmTarget.fromTarget(release.toString())
            if (overrideDefaults) jvmTarget.set(target) else jvmTarget.convention(target)
            jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
        }
    }
    val toolchains = extensions.getByType<JavaToolchainService>()
    tasks.withType<Test>().configureEach {
        javaLauncher.convention(
            toolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(toolchain))
            },
        )
    }
}

/** 讀取 Settings plugin 發布的 target Java toolchain。 */
internal fun Project.targetJavaToolchain(): Int = gradleExtras().get(PlatformTargetsSettingsPlugin.TARGET_JAVA_TOOLCHAIN_EXTRA) as Int

/** 讀取 Settings plugin 發布的 target Java release。 */
internal fun Project.targetJavaRelease(): Int = gradleExtras().get(PlatformTargetsSettingsPlugin.TARGET_JAVA_RELEASE_EXTRA) as Int

/** 讀取 Gradle instance 的 extra properties。 */
private fun Project.gradleExtras() = (gradle as ExtensionAware).extensions.extraProperties
