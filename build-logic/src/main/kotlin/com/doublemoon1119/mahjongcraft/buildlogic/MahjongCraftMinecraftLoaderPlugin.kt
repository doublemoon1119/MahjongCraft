package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.language.jvm.tasks.ProcessResources
import java.util.Optional
import java.util.concurrent.Callable

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
        packaging.distributable.convention(true)
        packaging.archiveBaseName.convention("$rootProjectId-${platformLoader()}-${platformMinecraftVersion()}")
        extensions.configure<BasePluginExtension> {
            archivesName.set(packaging.archiveBaseName)
        }
        val buildMetadata = buildMetadata()
        val modVersion = buildMetadata.map { metadata -> "$version+${platformMinecraftVersion()}-${platformLoader()}${metadata.map { ".$it" }.orElse("")}" }
        tasks.withType<AbstractArchiveTask>().configureEach {
            archiveVersion.set(buildMetadata.map { metadata -> "$version${metadata.map { "+$it" }.orElse("")}" })
        }
        tasks.withType<ProcessResources>().configureEach {
            val metadata = provider {
                mapOf(
                    "version" to modVersion.get(),
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
        contributeCiArtifact(packaging)
        serializeRemapTasks()
        alignLoomRuntimeJava()
        configureDevMods()
    }
}

/**
 * 把這個模組交付給玩家的 JAR 接到 root 的 [CI_ARTIFACTS_TASK_NAME]；[MinecraftModPackagingExtension.distributable] 為否時不接。
 *
 * Fabric Loom 模組交付的是重新映射後的 `remapJar`，其他模組是 `jar`。root 沒有套用 target 管理外掛時（例如只建置部分專案的
 * 測試建置）不做任何事。
 *
 * @param packaging 這個模組的打包設定。
 */
private fun Project.contributeCiArtifact(packaging: MinecraftModPackagingExtension) {
    val project = this
    rootProject.pluginManager.withPlugin("mahjongcraft.target-management") {
        rootProject.tasks.named(CI_ARTIFACTS_TASK_NAME, Sync::class.java).configure {
            from(
                Callable {
                    when {
                        !packaging.distributable.get() -> emptyList<Any>()
                        project.tasks.names.contains("remapJar") -> listOf(project.tasks.named("remapJar"))
                        else -> listOf(project.tasks.named("jar"))
                    }
                },
            )
        }
    }
}

/**
 * 模組目錄名稱代表的載入器，例如 `platform/minecraft/v1.20.1/fabric` 的 `fabric`。
 *
 * @return 載入器名稱。
 */
private fun Project.platformLoader(): String = projectDir.name

/**
 * 模組上層目錄名稱代表的 Minecraft 版本，例如 `platform/minecraft/v1.20.1/fabric` 的 `1.20.1`。
 *
 * @return 不含 `v` 前綴的 Minecraft 版本。
 */
private fun Project.platformMinecraftVersion(): String = projectDir.parentFile.name.removePrefix("v")

/**
 * [BUILD_METADATA_PROPERTY] 指定的建置資訊，例如 CI 傳入的 commit 編號；格式不符 SemVer 建置資訊時建置失敗。
 *
 * 模組 metadata 的版本固定寫成 `<專案版本>+<Minecraft 版本>-<載入器>`，有建置資訊時再接 `.<建置資訊>`；產出的檔名已含
 * 載入器與 Minecraft 版本，只在專案版本後接 `+<建置資訊>`。`+` 之後依 SemVer 不影響版本比較，專案版本與各模組的版本
 * 政策都不變。
 *
 * @return 建置資訊；沒有指定時為空。
 */
private fun Project.buildMetadata(): Provider<Optional<String>> {
    val property = providers.gradleProperty(BUILD_METADATA_PROPERTY)
    return provider {
        val metadata = property.orNull?.trim()?.takeIf(String::isNotEmpty)
        if (metadata != null && !BUILD_METADATA_PATTERN.matches(metadata)) {
            throw GradleException(
                "Invalid -P$BUILD_METADATA_PROPERTY value '$metadata'; use dot-separated letters, digits and hyphens",
            )
        }
        Optional.ofNullable(metadata)
    }
}

/** 附加在模組版本後的建置資訊，例如 CI 的 commit 編號。 */
private const val BUILD_METADATA_PROPERTY = "mahjongcraftBuildMetadata"

/** SemVer 建置資訊：以點分隔、每段為英數字與連字號。 */
private val BUILD_METADATA_PATTERN = Regex("[0-9A-Za-z-]+(\\.[0-9A-Za-z-]+)*")

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
