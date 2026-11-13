package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/** 套用 Minecraft loader module 的 target Java、archive 與 metadata 政策，並讓整個建置的重新映射任務排隊執行。 */
class MahjongCraftMinecraftLoaderPlugin : Plugin<Project> {
    /** 套用 JVM convention 並設定 loader-specific build 行為。 */
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("mahjongcraft.kotlin-jvm")
        configureKotlinJvm(targetJavaToolchain(), targetJavaRelease(), overrideDefaults = true)
        val packaging = extensions.create("minecraftModPackaging", MinecraftModPackagingExtension::class.java)
        packaging.dependencyRequirements.convention(emptyMap())
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
                    "dependencyRequirements" to packaging.dependencyRequirements.get(),
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
        serializeRemapTasks()
    }
}

/**
 * 讓這個模組的 Fabric Loom 重新映射任務使用整個建置共用的單一名額；其他任務照常並行。
 *
 * 以任務名稱比對，build-logic 不需要依賴 Loom；沒有套用 Loom 的 loader 模組不受影響。
 */
private fun Project.serializeRemapTasks() {
    val remapSerialization = gradle.sharedServices.registerIfAbsent(
        MINECRAFT_REMAP_SERIALIZATION_SERVICE,
        MinecraftRemapSerializationService::class.java,
    ) {
        maxParallelUsages.set(1)
    }
    pluginManager.withPlugin(FABRIC_LOOM_PLUGIN_ID) {
        tasks.matching { it.name in LOOM_REMAP_TASK_NAMES }.configureEach {
            usesService(remapSerialization)
        }
    }
}

/** 整個建置共用的重新映射排隊服務名稱。 */
private const val MINECRAFT_REMAP_SERIALIZATION_SERVICE = "minecraftRemapSerialization"

/** Fabric Loom 的 plugin ID。 */
private const val FABRIC_LOOM_PLUGIN_ID = "fabric-loom"

/** Fabric Loom 會讀取並重新映射 JAR 的任務。 */
private val LOOM_REMAP_TASK_NAMES: Set<String> = setOf("remapJar", "remapSourcesJar")
