package com.doublemoon1119.mahjongcraft.buildlogic

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/** 套用 MahjongCraft KMP module 的核心 Java baseline 與格式設定。 */
class MahjongCraftKotlinMultiplatformPlugin : Plugin<Project> {
    /** 套用 KMP、base、ktlint、detekt 與核心 JVM 編譯政策。 */
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("mahjongcraft.base")
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("org.jlleitschuh.gradle.ktlint")
        configureKtlint()
        configureDetekt()
        configureMultiplatformJvm(
            catalogVersion("core-java-toolchain").toInt(),
            catalogVersion("core-java-release").toInt(),
            overrideDefaults = false,
        )
    }
}

/** 套用 MahjongCraft Kotlin JVM module 的核心 Java baseline 與格式設定。 */
class MahjongCraftKotlinJvmPlugin : Plugin<Project> {
    /** 套用 Kotlin JVM、base、ktlint、detekt 與核心 JVM 編譯政策。 */
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("mahjongcraft.base")
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("org.jlleitschuh.gradle.ktlint")
        configureKtlint()
        configureDetekt()
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

/**
 * 套用 detekt，只執行共用設定檔明確啟用的規則，並註冊手動執行的 [DETEKT_ALL_TASK_NAME] 任務。
 *
 * detekt 不掛到 `check`，`build` 不會執行它。detekt 為每個 JVM compilation 建立帶型別解析的任務
 * （KMP 為 `detektMainJvm`／`detektTestJvm`，Kotlin JVM 為 `detektMain`／`detektTest`），
 * [DETEKT_ALL_TASK_NAME] 彙整這些任務；外掛預設掛到 `check` 的 `detekt` 任務與各 source set 的
 * `detekt*SourceSet` 任務不做型別解析，需要型別資訊的規則在其中不會執行，因此不納入彙整並從 `check` 移除。
 */
private fun Project.configureDetekt() {
    pluginManager.apply("dev.detekt")
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(false)
        config.setFrom(rootProject.layout.projectDirectory.file(DETEKT_CONFIG_PATH))
    }
    val typeResolvedDetektTasks = tasks.withType<Detekt>().matching { task ->
        task.name != PLAIN_DETEKT_TASK_NAME && !task.name.endsWith(SOURCE_SET_DETEKT_TASK_SUFFIX)
    }
    tasks.register(DETEKT_ALL_TASK_NAME) {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Runs detekt with type resolution on every compilation of this project."
        dependsOn(typeResolvedDetektTasks)
    }
    tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
        setDependsOn(dependsOn.filterNot { (it as? TaskProvider<*>)?.name == PLAIN_DETEKT_TASK_NAME })
    }
}

/** 共用 detekt 設定檔相對於 root project 的路徑。 */
private const val DETEKT_CONFIG_PATH: String = "config/detekt/detekt.yml"

/** 彙整帶型別解析 detekt 任務、供手動執行的任務名稱。 */
private const val DETEKT_ALL_TASK_NAME: String = "detektAll"

/** detekt 外掛建立、不做型別解析的整體任務名稱。 */
private const val PLAIN_DETEKT_TASK_NAME: String = "detekt"

/** detekt 外掛為各 source set 建立、不做型別解析的任務名稱後綴。 */
private const val SOURCE_SET_DETEKT_TASK_SUFFIX: String = "SourceSet"

/** 提供專案自訂 ktlint rule set 的模組路徑。 */
private const val CUSTOM_RULE_SET_PROJECT_PATH: String = ":ktlint-rules"
