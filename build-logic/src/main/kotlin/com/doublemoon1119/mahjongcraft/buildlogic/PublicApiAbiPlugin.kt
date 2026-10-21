package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

/**
 * 為明確指定範圍的模組啟用 Kotlin 公開 API 的 ABI 檢查。
 *
 * 每個套用此 convention 的模組都必須在 `publicApiAbi` 列出要保證的型別；
 * 外掛不會把所有 `api` 套件自動視為穩定介面。
 */
class PublicApiAbiPlugin : Plugin<Project> {
    /**
     * 建立範圍設定，並在 Kotlin 外掛套用後接入內建 ABI 驗證。
     *
     * @param project 要驗證公開介面的模組。
     */
    override fun apply(project: Project) = with(project) {
        val publicApiAbi = extensions.create<PublicApiAbiExtension>("publicApiAbi")

        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            configurePublicApiAbi(publicApiAbi)
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            configurePublicApiAbi(publicApiAbi)
        }
    }
}

/**
 * 將明確列出的公開型別範圍接到 Kotlin ABI 驗證。
 *
 * @param extension 此模組的 ABI 範圍設定。
 */
@OptIn(ExperimentalAbiValidation::class)
private fun Project.configurePublicApiAbi(extension: PublicApiAbiExtension) {
    extensions.configure<KotlinBaseExtension> {
        abiValidation {
            filters {
                include {
                    byNames.set(
                        extension.includedNames.map { names ->
                            require(names.isNotEmpty()) {
                                "publicApiAbi.includedNames must contain at least one ABI filter"
                            }
                            require(names.none { it.isBlank() }) {
                                "publicApiAbi.includedNames must not contain blank ABI filters"
                            }
                            names.toSet()
                        },
                    )
                }
            }
        }
    }
}
