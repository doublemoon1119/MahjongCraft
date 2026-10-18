package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/** 套用 Minecraft loader module 的 target Java、archive 與 metadata 政策。 */
class MahjongCraftMinecraftLoaderPlugin : Plugin<Project> {
    /** 套用 JVM convention 並設定 loader-specific build 行為。 */
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("mahjongcraft.kotlin-jvm")
        configureKotlinJvm(targetJavaToolchain(), targetJavaRelease(), overrideDefaults = true)
        val packaging = extensions.create("minecraftModPackaging", MinecraftModPackagingExtension::class.java)
        val rootProjectId = rootProject.extensions.extraProperties["projectId"].toString()
        packaging.modId.convention(rootProjectId)
        packaging.displayName.convention(rootProject.extensions.extraProperties["projectDisplayName"].toString())
        packaging.description.convention("Bring Mahjong to Minecraft and play with your friends.")
        packaging.icon.convention(rootProject.layout.projectDirectory.file("docs/assets/branding/logo.png"))
        packaging.archiveBaseName.convention("$rootProjectId-${projectDir.name}-${projectDir.parentFile.name.removePrefix("v")}")
        extensions.configure<BasePluginExtension> {
            archivesName.set(packaging.archiveBaseName)
        }
        tasks.withType<ProcessResources>().configureEach {
            val metadata = provider {
                mapOf(
                    "version" to version.toString(),
                    "id" to packaging.modId.get(),
                    "name" to packaging.displayName.get(),
                    "description" to packaging.description.get(),
                    "license" to "MIT",
                    "author" to "doublemoon1119",
                    "homepage" to "https://github.com/doublemoon1119/MahjongCraft",
                    "sources" to "https://github.com/doublemoon1119/MahjongCraft",
                    "issues" to "https://github.com/doublemoon1119/MahjongCraft/issues",
                )
            }
            inputs.file(packaging.icon).withPropertyName("projectLogo")
            from(packaging.icon) {
                into(packaging.modId.map { "assets/$it" })
                rename { "icon.png" }
            }
            inputs.property("modMetadata", metadata)
            filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) {
                expand(metadata.get())
            }
        }
    }
}
