package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider

/**
 * 開發用戶端啟動前放入並開啟光影包（[PrepareDevShaderPackTask]）。
 *
 * - 由 [MinecraftDevModsExtension.shaderPack] 在組合被選中時呼叫。
 *
 * @param pack 光影包，以 zip 檔取得。
 * @param clientTask 開發用戶端的執行任務名稱。
 */
internal fun Project.registerDevShaderPack(pack: Provider<MinimalExternalModuleDependency>, clientTask: String = "runClient") {
    val packs = configurations.create("devShaderPack") {
        isCanBeConsumed = false
        isTransitive = false
    }
    dependencies.addProvider<MinimalExternalModuleDependency, ExternalModuleDependency>(packs.name, pack) {
        artifact {
            name = this@addProvider.name
            type = SHADER_PACK_EXTENSION
            extension = SHADER_PACK_EXTENSION
        }
    }
    val prepare = tasks.register("prepareDevShaderPack", PrepareDevShaderPackTask::class.java) {
        group = "development"
        description = "Puts the development shader pack into the client run directory and turns it on."
        runDirectory.set(layout.projectDirectory.dir(clientRunDirectory()))
        shaderPack.from(packs)
    }
    tasks.named(clientTask) { dependsOn(prepare) }
}

/** 光影包檔案的副檔名。 */
private const val SHADER_PACK_EXTENSION = "zip"
