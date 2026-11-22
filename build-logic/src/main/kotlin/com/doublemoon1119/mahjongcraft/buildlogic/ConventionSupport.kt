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
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
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
 * 設定 Java/Kotlin bytecode release、Kotlin API 版本上限與測試 launcher。
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
    // 程式只能使用執行環境提供的 Kotlin 標準函式庫中已有的 API；編譯器本身可以較新。
    val kotlinApiVersion = KotlinVersion.fromVersion(catalogVersion(KOTLIN_API_VERSION_ALIAS))
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions.apiVersion.set(kotlinApiVersion)
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

/** `libs` version catalog 中 Kotlin API 版本上限的名稱。 */
internal const val KOTLIN_API_VERSION_ALIAS: String = "kotlin-api-version"

/** 讀取 Settings plugin 發布的 target Java toolchain。 */
internal fun Project.targetJavaToolchain(): Int = gradleExtras().get(PlatformTargetsSettingsPlugin.TARGET_JAVA_TOOLCHAIN_EXTRA) as Int

/**
 * 讀取 Settings plugin 發布的 target 執行環境提供的函式庫版本；core target 沒有。
 *
 * @return 以 `libs` version catalog 版本名稱為 key 的版本。
 */
@Suppress("UNCHECKED_CAST")
internal fun Project.targetProvidedVersions(): Map<String, String> =
    gradleExtras().get(PlatformTargetsSettingsPlugin.TARGET_PROVIDED_VERSIONS_EXTRA) as Map<String, String>

/**
 * 把測試的工作目錄放在 build 目錄中；測試期間 Minecraft 的記錄檔等以相對路徑建立的檔案可由 clean 移除。
 */
internal fun Project.isolateTestRunDirectory() {
    tasks.withType<Test>().configureEach {
        val testRunDirectory = layout.buildDirectory.dir("test-runs/$name")
        workingDir(testRunDirectory)
        doFirst {
            testRunDirectory.get().asFile.mkdirs()
        }
    }
}

/** 讀取 Settings plugin 發布的 target Java release。 */
internal fun Project.targetJavaRelease(): Int = gradleExtras().get(PlatformTargetsSettingsPlugin.TARGET_JAVA_RELEASE_EXTRA) as Int

/** 讀取 Gradle instance 的 extra properties。 */
private fun Project.gradleExtras() = (gradle as ExtensionAware).extensions.extraProperties
