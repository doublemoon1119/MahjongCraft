package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.maven
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import java.io.File

/**
 * 把另一個專案的原始碼與資源一起編譯並打包進這個模組。
 *
 * 用於載入器模組納入版本 mod 模組等原始碼：那些模組的 JAR 使用 Mojang 名稱，各載入器要把它們與自己的程式一起轉成執行時
 * 的名稱。不存在的資料夾會被略過。
 *
 * @param projectPath 提供原始碼的專案路徑。
 * @param sourceDirectory 相對於該專案的原始碼資料夾，例如 `src/main` 或 Kotlin 多平台模組的 `src/jvmMain`。
 */
fun Project.includeSourcesOf(projectPath: String, sourceDirectory: String = "src/main") {
    val directory = project(projectPath).projectDir.resolve(sourceDirectory)
    extensions.getByType<SourceSetContainer>().named("main").configure {
        java.srcDir(directory.resolve("java"))
        resources.srcDir(directory.resolve("resources"))
    }
    extensions.getByType<KotlinJvmProjectExtension>().sourceSets.named("main").configure {
        kotlin.srcDir(directory.resolve("kotlin"))
    }
}

/**
 * 把另一個專案中只給其他模組使用的測試原始碼，加入這個模組的測試一起編譯與執行。
 *
 * 用於各載入器共用、但需要載入器提供部分內容才能執行的測試；提供原始碼的專案自己不編譯它們。
 *
 * @param projectPath 提供測試原始碼的專案路徑。
 * @param sourceDirectory 相對於該專案的 Kotlin 原始碼資料夾。
 */
fun Project.includeTestSourcesOf(projectPath: String, sourceDirectory: String) {
    val directory = project(projectPath).projectDir.resolve(sourceDirectory)
    extensions.getByType<KotlinJvmProjectExtension>().sourceSets.named("test").configure {
        kotlin.srcDir(directory)
    }
}

/**
 * 開發執行要傳給遊戲的自動測試系統屬性，由 Gradle 屬性 `mahjongcraftAutotest` 決定。
 *
 * 屬性值原樣傳給 debug 模組，例如 `true` 執行全部步驟、`world,screens` 只執行列出的步驟；另外傳入這個模組的名稱，例如
 * `minecraft_v1.20.1_fabric`，供報告記錄測試的目標。Gradle 屬性 `mahjongcraftAutotestKeep` 指定要保留最近幾次的報告，
 * 沒有指定時由 debug 模組使用預設值。沒有設定 `mahjongcraftAutotest` 時為空，開發執行與平常相同。
 *
 * @return 系統屬性名稱與值。
 * @throws GradleException `mahjongcraftAutotestKeep` 不是正整數時拋出。
 */
fun Project.autotestSystemProperties(): Map<String, String> {
    val selection = providers.gradleProperty("mahjongcraftAutotest").orNull?.takeIf(String::isNotBlank) ?: return emptyMap()
    val keep = providers.gradleProperty("mahjongcraftAutotestKeep").orNull?.let { value ->
        value.toIntOrNull()?.takeIf { it >= 1 } ?: throw GradleException("mahjongcraftAutotestKeep must be a positive integer: $value")
    }
    return mapOf("mahjongcraft.autotest" to selection, "mahjongcraft.autotest.target" to name) +
        listOfNotNull(keep?.let { "mahjongcraft.autotest.keep" to it.toString() })
}

/**
 * 自動測試的開發用戶端要加上的環境變數：讓 Minecraft 使用的音訊庫 OpenAL Soft 使用空輸出，聲音照常載入與播放但聽不到，
 * 也不改動遊戲的音量或輸出裝置設定。沒有設定 `mahjongcraftAutotest` 時為空。
 *
 * @return 環境變數名稱與值。
 */
fun Project.autotestClientEnvironment(): Map<String, String> =
    if (autotestSystemProperties().isEmpty()) emptyMap() else mapOf("ALSOFT_DRIVERS" to "null")

/**
 * 自動測試的開發用戶端要加上的程式參數：固定玩家名稱與視窗大小。開發環境的玩家 UUID 由名稱決定，固定後每次測試的玩家、
 * 房主與截圖中的名稱都相同；固定的視窗大小讓每次的截圖可以互相比對。沒有設定 `mahjongcraftAutotest` 時為空。
 *
 * @return 程式參數。
 */
fun Project.autotestClientArguments(): List<String> = if (autotestSystemProperties().isEmpty()) {
    emptyList()
} else {
    listOf("--username", "MahjongTest", "--width", "1920", "--height", "1080")
}

/**
 * 開發用戶端的執行目錄名稱，相對於模組資料夾。自動測試使用獨立的 `run/autotest-client`，它的設定、世界與報告不會與平常的
 * `run/client` 混在一起。
 *
 * @return 執行目錄名稱。
 */
fun Project.clientRunDirectory(): String = if (autotestSystemProperties().isEmpty()) "run/client" else "run/autotest-client"

/**
 * 把另一個專案的資源一起打包進這個模組，例如跨版本共用模組的材質與翻譯。
 *
 * @param projectPath 提供資源的專案路徑。
 * @param sourceDirectory 相對於該專案的原始碼資料夾，例如 Kotlin 多平台模組的 `src/jvmMain`。
 */
fun Project.includeResourcesOf(projectPath: String, sourceDirectory: String) {
    val directory = project(projectPath).projectDir.resolve(sourceDirectory)
    extensions.getByType<SourceSetContainer>().named("main").configure {
        resources.srcDir(directory.resolve("resources"))
    }
}

/**
 * 這個 Minecraft 版本的 mod 模組所用的 access transformer，例如 `:minecraft_v1.20.1_mod` 的那份。
 *
 * 模組放在 Minecraft 版本資料夾底下，例如 `platform/minecraft/v1.20.1/forge` 或範例的 `example/platform/minecraft/v1.20.1/mod`；
 * 版本取自上層資料夾的名稱。Forge 與以原版編譯的模組都讀這一份，本體在執行時也套用同一份。
 *
 * @return access transformer 檔案。
 */
fun Project.minecraftModAccessTransformer(): File =
    project(":minecraft_${projectDir.parentFile.name}_mod").file("src/main/resources/META-INF/accesstransformer.cfg")

/**
 * 不顯示 Forge 對原版寫法的過時警告。
 *
 * Forge 把部分原版寫法標成過時，要 mod 改用 Forge 的寫法；與 Fabric 共用的程式只能用原版寫法。共用程式真正的過時寫法仍由
 * 以原版編譯的 mod 模組與 Fabric 的編譯提醒。
 */
fun Project.hideForgeDeprecationsOfVanillaApis() {
    extensions.getByType<KotlinJvmProjectExtension>().compilerOptions {
        freeCompilerArgs.addAll("-Xwarning-level=DEPRECATION:disabled", "-Xwarning-level=OVERRIDE_DEPRECATION:disabled")
    }
}

/** 加入 Kotlin for Forge 作者自己的 Maven 倉庫；它只提供 Kotlin for Forge 的套件，其他套件不會向它查詢。 */
fun RepositoryHandler.kotlinForForge() {
    exclusiveContent {
        forRepository { maven("https://thedarkcolour.github.io/KotlinForForge/") { name = "KotlinForForge" } }
        filter { includeGroup("thedarkcolour") }
    }
}

/** 加入 Modrinth 的 Maven 倉庫；它只提供 Modrinth 的套件，其他套件不會向它查詢。 */
fun RepositoryHandler.modrinth() {
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}
