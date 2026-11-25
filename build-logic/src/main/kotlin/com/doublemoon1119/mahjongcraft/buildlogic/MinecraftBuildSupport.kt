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
 * - 用於載入器模組納入版本 mod 模組等原始碼。那些模組的 JAR 使用 Mojang 名稱，各載入器要把它們與自己的程式一起轉成執行時
 *   的名稱。
 * - 提供原始碼的是 Minecraft 版本資料夾中的模組時（例如 `platform/minecraft/v1.20.1/mod`），跨版本共用的同名資料夾
 *   （[sharedVersionDirectory]）中相同位置的原始碼也一併納入。
 * - 不存在的資料夾會被略過。
 *
 * @param projectPath 提供原始碼的專案路徑。
 * @param sourceDirectory 相對於該專案的原始碼資料夾，例如 `src/main` 或 Kotlin 多平台模組的 `src/jvmMain`。
 */
fun Project.includeSourcesOf(projectPath: String, sourceDirectory: String = "src/main") {
    val provider = project(projectPath).projectDir
    listOfNotNull(sharedVersionDirectory(provider), provider).map { it.resolve(sourceDirectory) }.forEach { directory ->
        addMainSources(directory)
    }
}

/**
 * 把另一個專案中只給其他模組使用的測試原始碼，加入這個模組的測試一起編譯與執行。
 *
 * - 跨版本共用的同名資料夾中相同位置的測試也一併納入。
 * - 用於各載入器共用、但需要載入器提供部分內容才能執行的測試。提供原始碼的專案自己不編譯它們。
 *
 * @param projectPath 提供測試原始碼的專案路徑。
 * @param sourceDirectory 相對於該專案的 Kotlin 原始碼資料夾。
 */
fun Project.includeTestSourcesOf(projectPath: String, sourceDirectory: String) {
    val provider = project(projectPath).projectDir
    listOfNotNull(sharedVersionDirectory(provider), provider).map { it.resolve(sourceDirectory) }.forEach { directory ->
        extensions.getByType<KotlinJvmProjectExtension>().sourceSets.named("test").configure {
            kotlin.srcDir(directory)
        }
    }
}

/**
 * 讓 Minecraft 版本資料夾中的模組一起編譯跨版本共用的原始碼、資源與測試。
 *
 * - 例如 `platform/minecraft/v1.20.1/mod` 編譯 `platform/minecraft/mod` 的 `src/main` 與 `src/test`。
 * - 共用的資料夾本身不是 Gradle 專案，只能對某個 Minecraft 版本編譯。
 * - 兩個版本 API 不同的部分，放在各版本自己的資料夾。
 */
fun Project.includeSharedVersionSources() {
    val shared = checkNotNull(sharedVersionDirectory(projectDir)) { "$path is not in a Minecraft version folder" }
    addMainSources(shared.resolve("src/main"))
    extensions.getByType<SourceSetContainer>().named("test").configure {
        resources.srcDir(shared.resolve("src/test/resources"))
    }
    extensions.getByType<KotlinJvmProjectExtension>().sourceSets.named("test").configure {
        kotlin.srcDir(shared.resolve("src/test/kotlin"))
    }
}

/**
 * Minecraft 版本資料夾中的模組對應的跨版本共用資料夾：`<平台>/v<版本>/<模組>` 對應 `<平台>/<模組>`，例如
 * `platform/minecraft/v1.20.1/mod` 對應 `platform/minecraft/mod`。
 *
 * @param moduleDirectory 模組資料夾。
 * @return 共用資料夾。模組不在 Minecraft 版本資料夾中或沒有共用資料夾時為 null。
 */
fun sharedVersionDirectory(moduleDirectory: File): File? {
    val versionDirectory = moduleDirectory.parentFile ?: return null
    if (!VERSION_DIRECTORY.matches(versionDirectory.name)) return null
    return versionDirectory.parentFile?.resolve(moduleDirectory.name)?.takeIf(File::isDirectory)
}

/**
 * 把一個原始碼資料夾中的 Java、Kotlin 與資源加入主程式。
 *
 * @param directory 原始碼資料夾，例如 `src/main`。
 */
private fun Project.addMainSources(directory: File) {
    extensions.getByType<SourceSetContainer>().named("main").configure {
        java.srcDir(directory.resolve("java"))
        resources.srcDir(directory.resolve("resources"))
    }
    extensions.getByType<KotlinJvmProjectExtension>().sourceSets.named("main").configure {
        kotlin.srcDir(directory.resolve("kotlin"))
    }
}

/** Minecraft 版本資料夾的名稱，例如 `v1.20.1`。 */
private val VERSION_DIRECTORY = Regex("""v\d+(\.\d+)+""")

/**
 * `mahjongcraftAutotest` 的值是否開啟自動測試，判定方式與 debug 模組讀取系統屬性時相同。
 *
 * - 沒有設定、空白、`false`（不分大小寫）：關閉。
 * - 其他值：開啟，值是要執行的步驟。
 *
 * @param value Gradle 屬性的值。沒有設定時為 null。
 * @return 開啟時為 true。
 */
internal fun isAutotestEnabled(value: String?): Boolean {
    val trimmed = value?.trim().orEmpty()
    return trimmed.isNotEmpty() && !trimmed.equals("false", ignoreCase = true)
}

/**
 * 開發執行要傳給遊戲的自動測試系統屬性，由 Gradle 屬性 `mahjongcraftAutotest` 決定。
 *
 * - `mahjongcraftAutotest` 的值原樣傳給 debug 模組，例如 `true` 執行全部步驟、`world,screens` 只執行列出的步驟。
 * - 另外傳入這個模組的名稱，例如 `minecraft_v1.20.1_fabric`，供報告記錄測試的目標。
 * - `mahjongcraftAutotestKeep` 指定保留最近幾次的報告，沒有指定時由 debug 模組使用預設值。
 * - 自動測試沒有開啟時（[isAutotestEnabled]）為空，開發執行與平常相同。
 *
 * @return 系統屬性名稱與值。
 * @throws GradleException `mahjongcraftAutotestKeep` 不是正整數時拋出。
 */
fun Project.autotestSystemProperties(): Map<String, String> {
    val selection = providers.gradleProperty("mahjongcraftAutotest").orNull?.takeIf(::isAutotestEnabled) ?: return emptyMap()
    val keep = providers.gradleProperty("mahjongcraftAutotestKeep").orNull?.let { value ->
        value.toIntOrNull()?.takeIf { it >= 1 } ?: throw GradleException("mahjongcraftAutotestKeep must be a positive integer: $value")
    }
    return mapOf("mahjongcraft.autotest" to selection, "mahjongcraft.autotest.target" to name) +
        listOfNotNull(keep?.let { "mahjongcraft.autotest.keep" to it.toString() })
}

/**
 * 自動測試的開發用戶端要加上的環境變數。
 *
 * - 讓 Minecraft 使用的音訊庫 OpenAL Soft 使用空輸出：聲音照常載入與播放，但聽不到。
 * - 不改動遊戲的音量或輸出裝置設定。
 * - 沒有設定 `mahjongcraftAutotest` 時為空。
 *
 * @return 環境變數名稱與值。
 */
fun Project.autotestClientEnvironment(): Map<String, String> =
    if (autotestSystemProperties().isEmpty()) emptyMap() else mapOf("ALSOFT_DRIVERS" to "null")

/**
 * 自動測試的開發用戶端要加上的程式參數。
 *
 * - 固定玩家名稱：開發環境的玩家 UUID 由名稱決定，每次測試的玩家、房主與截圖中的名稱都相同。
 * - 固定視窗大小：每次的截圖可以互相比對。
 * - 沒有設定 `mahjongcraftAutotest` 時為空。
 *
 * @return 程式參數。
 */
fun Project.autotestClientArguments(): List<String> = if (autotestSystemProperties().isEmpty()) {
    emptyList()
} else {
    listOf("--username", "MahjongTest", "--width", "1920", "--height", "1080")
}

/**
 * 開發用戶端的執行目錄名稱，相對於模組資料夾。
 *
 * - 平常：`run/client`。
 * - 自動測試：獨立的 `run/autotest-client`，設定、世界與報告不會與平常的混在一起。
 *
 * @return 執行目錄名稱。
 */
fun Project.clientRunDirectory(): String = if (autotestSystemProperties().isEmpty()) "run/client" else "run/autotest-client"

/**
 * 開發伺服器的執行目錄名稱，相對於模組資料夾。
 *
 * - 平常：`run/server`。
 * - 伺服器自動測試：獨立的 `run/autotest-server`，測試世界、設定與報告不會與平常的混在一起。
 *
 * @return 執行目錄名稱。
 */
fun Project.serverRunDirectory(): String = if (autotestSystemProperties().isEmpty()) "run/server" else "run/autotest-server"

/**
 * 伺服器自動測試某一段要傳給伺服器的系統屬性。
 *
 * - 內容是 [autotestSystemProperties] 加上這是第幾段。
 * - 沒有設定 `mahjongcraftAutotest` 時為空。
 *
 * @param phase 第幾段，[AUTOTEST_SERVER_START] 或 [AUTOTEST_SERVER_RESUME]。
 * @return 系統屬性名稱與值。
 */
fun Project.autotestServerSystemProperties(phase: String): Map<String, String> =
    autotestSystemProperties().takeIf { it.isNotEmpty() }?.plus("mahjongcraft.autotest.serverPhase" to phase).orEmpty()

/** 伺服器自動測試的第一段：準備好的新世界上開局，打到一半關機。 */
const val AUTOTEST_SERVER_START: String = "start"

/** 伺服器自動測試的第二段：讀回第一段留下的世界，確認恢復後打完。 */
const val AUTOTEST_SERVER_RESUME: String = "resume"

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
 * 模組放在 Minecraft 版本資料夾底下，例如 `platform/minecraft/v1.20.1/forge` 或範例的 `example/platform/minecraft/v1.20.1/mod`。
 * 版本取自上層資料夾的名稱。Forge 與以原版編譯的模組都讀這一份，本體在執行時也套用同一份。
 *
 * @return access transformer 檔案。
 */
fun Project.minecraftModAccessTransformer(): File =
    project(":minecraft_${projectDir.parentFile.name}_mod").file("src/main/resources/META-INF/accesstransformer.cfg")

/**
 * 不顯示 Forge 對原版寫法的過時警告。
 *
 * Forge 把部分原版寫法標成過時，要 mod 改用 Forge 的寫法。與 Fabric 共用的程式只能用原版寫法。共用程式真正的過時寫法仍由
 * 以原版編譯的 mod 模組與 Fabric 的編譯提醒。
 */
fun Project.hideForgeDeprecationsOfVanillaApis() {
    extensions.getByType<KotlinJvmProjectExtension>().compilerOptions {
        freeCompilerArgs.addAll("-Xwarning-level=DEPRECATION:disabled", "-Xwarning-level=OVERRIDE_DEPRECATION:disabled")
    }
}

/** 加入 Kotlin for Forge 作者自己的 Maven 倉庫。它只提供 Kotlin for Forge 的套件，其他套件不會向它查詢。 */
fun RepositoryHandler.kotlinForForge() {
    exclusiveContent {
        forRepository { maven("https://thedarkcolour.github.io/KotlinForForge/") { name = "KotlinForForge" } }
        filter { includeGroup("thedarkcolour") }
    }
}

/** 加入 Modrinth 的 Maven 倉庫。它只提供 Modrinth 的套件，其他套件不會向它查詢。 */
fun RepositoryHandler.modrinth() {
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}
