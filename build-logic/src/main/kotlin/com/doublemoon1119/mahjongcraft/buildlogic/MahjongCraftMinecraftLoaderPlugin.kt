package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * 套用 Minecraft loader module 的 target Java、archive 與 metadata 政策，讓整個建置的重新映射任務排隊執行，並提供
 * 開發執行時可選載入的模組組合。
 */
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
        alignLoomRuntimeJava()
        configureDevMods()
    }
}

/**
 * 讓 Fabric Loom 依目標的 Java 版本決定 Minecraft 的執行函式庫。
 *
 * Loom 預設以執行 Gradle 的 Java 判斷，Gradle 跑在 Java 19 以上時會把 LWJGL 換成較新的版本；開發執行實際使用目標的
 * Java 工具鏈，換掉後會與玩家的環境不同，例如 Sodium 會拒絕啟動。已經手動指定時不覆寫。
 */
private fun Project.alignLoomRuntimeJava() {
    if (findProperty(LOOM_RUNTIME_JAVA_PROPERTY) == null) {
        extensions.extraProperties[LOOM_RUNTIME_JAVA_PROPERTY] = targetJavaToolchain().toString()
    }
}

/** Fabric Loom 決定執行函式庫時使用的 Java 版本 property。 */
private const val LOOM_RUNTIME_JAVA_PROPERTY = "fabric.loom.runtimeJavaCompatibilityVersion"

/**
 * 建立開發執行時可選載入模組的 `devMods` 擴充；開關選中卻沒有宣告的組合在專案設定完成後報錯。
 *
 * Fabric Loom 模組預設把選中的組合加入只在開發執行時載入的 `modLocalRuntime`。
 */
private fun Project.configureDevMods() {
    val selected = MinecraftDevMods.parse(providers.gradleProperty(MinecraftDevMods.PROPERTY_NAME).orNull)
    val devMods = extensions.create("devMods", MinecraftDevModsExtension::class.java, selected, dependencies)
    pluginManager.withPlugin(FABRIC_LOOM_PLUGIN_ID) {
        devMods.configurationName.convention(LOOM_LOCAL_RUNTIME_CONFIGURATION)
    }
    afterEvaluate {
        MinecraftDevMods.requireDeclared(path, selected, devMods.groupNames)
    }
}

/** Fabric Loom 只在開發執行時載入、不打包也不發布的模組設定。 */
private const val LOOM_LOCAL_RUNTIME_CONFIGURATION = "modLocalRuntime"

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
