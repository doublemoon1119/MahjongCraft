package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

/**
 * 為明確套用此 convention 的模組啟用事件公開介面的 ABI 檢查。
 *
 * 僅納入 `api.event` 套件，不將事件交付、投影與其他實作型別納入相容承諾。
 * KMP 的驗證目標隨模組已宣告的 target 決定，不額外新增或限制平台。
 */
class EventApiAbiPlugin : Plugin<Project> {
    /**
     * 等待 Kotlin 外掛套用後啟用內建 ABI 驗證，由外掛將檢查接入 `check`。
     *
     * @param project 要驗證事件公開介面的模組。
     */
    override fun apply(project: Project) = with(project) {
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") { configureEventApiAbi() }
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") { configureEventApiAbi() }
    }
}

/** 啟用 Kotlin 內建 ABI 驗證並限制為事件 API 套件。 */
@OptIn(ExperimentalAbiValidation::class)
private fun Project.configureEventApiAbi() {
    extensions.configure<KotlinBaseExtension> {
        abiValidation {
            filters {
                include {
                    byNames.add("**.api.event.**")
                }
            }
        }
    }
}
