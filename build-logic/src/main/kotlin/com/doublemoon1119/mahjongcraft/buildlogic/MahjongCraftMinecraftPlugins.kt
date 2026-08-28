package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/** 套用 Minecraft 版本特定 common module 的 target Java 政策。 */
class MahjongCraftMinecraftVersionCommonPlugin : Plugin<Project> {
    /** 套用 KMP convention 並覆寫為目前 target 的 Java 設定。 */
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("mahjongcraft.kotlin-multiplatform")
        configureMultiplatformJvm(targetJavaToolchain(), targetJavaRelease(), overrideDefaults = true)
    }
}

/** 套用 Minecraft loader module 的 target Java、archive 與 metadata 政策。 */
class MahjongCraftMinecraftLoaderPlugin : Plugin<Project> {
    /** 套用 JVM convention 並設定 loader-specific build 行為。 */
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("mahjongcraft.kotlin-jvm")
        configureKotlinJvm(targetJavaToolchain(), targetJavaRelease(), overrideDefaults = true)
        plugins.withId("fabric-loom") {
            val loader = projectDir.name
            val minecraftVersion = projectDir.parentFile.name.removePrefix("v")
            extensions.configure<BasePluginExtension> {
                archivesName.set("${rootProject.extensions.extraProperties["projectId"]}-$loader-$minecraftVersion")
            }
        }
        tasks.withType<ProcessResources>().configureEach {
            val metadata = provider {
                mapOf(
                    "version" to version.toString(),
                    "id" to rootProject.extensions.extraProperties["projectId"].toString(),
                    "name" to rootProject.extensions.extraProperties["projectDisplayName"].toString(),
                    "description" to "Play Japanese (Riichi) Mahjong with your friends.",
                    "license" to "MIT",
                    "author" to "doublemoon1119",
                    "homepage" to "https://github.com/doublemoon1119/MahjongCraft",
                    "sources" to "https://github.com/doublemoon1119/MahjongCraft",
                    "issues" to "https://github.com/doublemoon1119/MahjongCraft/issues",
                )
            }
            inputs.property("modMetadata", metadata)
            filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) {
                expand(metadata.get())
            }
        }
    }
}
