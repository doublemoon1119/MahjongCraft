package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import java.io.File

/**
 * 在 root project 集中註冊 repository 層級的驗證，以及語系檔排序任務。
 *
 * 各任務需要的 project 資料都在設定階段以 provider 交給任務屬性，執行時不讀取 `Task.project`，
 * 因此相容於 configuration cache。
 */
class RepositoryVerificationPlugin : Plugin<Project> {
    /** 註冊驗證任務並統一串接至 root `check`。 */
    override fun apply(project: Project) {
        require(project == project.rootProject) { "mahjongcraft.repository-verification can only be applied to the root project" }
        project.pluginManager.apply("base")
        val rootDirectory = project.layout.projectDirectory
        val verifyNoDocsTempReferences = project.tasks.register(
            "verifyNoDocsTempReferences",
            VerifyNoDocsTempReferencesTask::class.java,
        ) {
            group = "verification"
            description = "Fails if any Kotlin source references a file under the gitignored docs/temp directory."
            this.rootDirectory.set(rootDirectory)
            projectDirectories.set(project.provider { project.rootProject.allprojects.map { it.projectDir } })
        }
        val verifyJsonFormatBoundary = project.tasks.register(
            "verifyJsonFormatBoundary",
            VerifyJsonFormatBoundaryTask::class.java,
        ) {
            group = "verification"
            description = "Fails if modules outside the serialization boundary use kotlinx.serialization.json in production code."
            this.rootDirectory.set(rootDirectory)
            guardedProjectDirectories.set(
                project.provider {
                    project.rootProject.allprojects.filter { it.path in JSON_FREE_PROJECT_PATHS }.map { it.projectDir }
                },
            )
        }
        val verifyProjectVersionPolicy = project.tasks.register(
            "verifyProjectVersionPolicy",
            VerifyProjectVersionPolicyTask::class.java,
        ) {
            group = "verification"
            description = "Verifies every loaded project uses its declared MahjongCraft release-train version."
            this.rootDirectory.set(rootDirectory)
            projectVersions.set(project.provider { project.rootProject.allprojects.associate { it.path to it.version.toString() } })
            projectDirectories.set(project.provider { project.rootProject.allprojects.associate { it.path to it.projectDir } })
            releaseTrainVersions.set(project.provider { releaseTrainVersions(project) })
        }
        val verifyModuleReadmes = project.tasks.register("verifyModuleReadmes", VerifyModuleReadmesTask::class.java) {
            group = "verification"
            description = "Verifies that every module and platform index has an exact README.md file."
            this.rootDirectory.set(rootDirectory)
            expectedReadmes.set(project.provider { expectedModuleReadmes(project) })
        }
        val langDirectory = rootDirectory.dir(MINECRAFT_LANG_DIRECTORY)
        project.tasks.register("sortMinecraftLangFiles", SortMinecraftLangFilesTask::class.java) {
            group = "formatting"
            description = "Sorts MahjongCraft Minecraft language files by translation key."
            this.langDirectory.set(langDirectory)
        }
        val verifyMinecraftLangFileKeyOrder = project.tasks.register(
            "verifyMinecraftLangFileKeyOrder",
            VerifyMinecraftLangFileKeyOrderTask::class.java,
        ) {
            group = "verification"
            description = "Verifies MahjongCraft Minecraft language files are sorted by translation key."
            this.rootDirectory.set(rootDirectory)
            this.langDirectory.set(langDirectory)
        }
        project.tasks.named("check").configure {
            dependsOn(
                verifyNoDocsTempReferences,
                verifyJsonFormatBoundary,
                verifyProjectVersionPolicy,
                verifyModuleReadmes,
                verifyMinecraftLangFileKeyOrder,
            )
        }
    }
}

/** Minecraft 平台語系檔目錄，相對於 root project。 */
private const val MINECRAFT_LANG_DIRECTORY: String = "platform/minecraft/common/src/jvmMain/resources/assets/mahjongcraft/lang"

/** 正式程式碼不得使用 JSON 型別的 project；序列化格式只屬於存檔、網路 DTO 與平台轉接層。 */
private val JSON_FREE_PROJECT_PATHS: Set<String> = setOf(
    ":mahjong-logic",
    ":mahjong-ai",
    ":mahjong-flow:mahjong-flow-common",
    ":mahjong-flow:mahjong-flow-server",
    ":mahjong-flow:mahjong-flow-client",
    ":mahjong-extension-api",
    ":mahjong-bundled-extensions",
    ":example:example-mahjong-extension",
)

/** version policy 使用的 release train 版本名稱。 */
private val RELEASE_TRAIN_VERSION_NAMES: List<String> = listOf(
    "minecraft-mod-version",
    "logic-version",
    "flow-version",
    "ai-version",
    "extension-api-version",
    "bundled-extensions-version",
)

/**
 * 從 `libs` version catalog 讀取所有 release train 版本。
 *
 * @param project 提供 root version catalog 的 project。
 * @return 以 catalog alias 為 key 的 release train 版本。
 */
private fun releaseTrainVersions(project: Project): Map<String, String> {
    val catalog = project.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
    return RELEASE_TRAIN_VERSION_NAMES.associateWith { name -> catalog.findVersion(name).get().requiredVersion }
}

/**
 * 取得目錄中的所有語系檔並依檔名排序；模組未載入時目錄仍在磁碟上，不受目前 target 選擇影響。
 *
 * @param dir 要搜尋語系檔的目錄。
 * @return 依檔名排序的 JSON 語系檔；目錄不存在時為空清單。
 */
internal fun minecraftLangFiles(dir: File): List<File> {
    if (!dir.isDirectory) return emptyList()
    return dir.listFiles { file -> file.isFile && file.extension == "json" }
        ?.sortedBy { it.name }
        ?: emptyList()
}

/**
 * 判斷語系檔內容是否已依 key 字典序排列。
 *
 * 換行符號不影響判斷：CRLF 與 LF 內容相同的檔案視為相同。
 *
 * @param text 要檢查的語系檔內容。
 * @return 條目已依 key 字典序排列時為 true。
 * @throws IllegalArgumentException [text] 不符合 [sortedLangFileText] 要求的首尾行與結尾換行格式。
 */
internal fun isLangFileSorted(text: String): Boolean = text.withLfLineEndings() == sortedLangFileText(text)

/**
 * 將語系檔內容依 key 字典序重排。
 *
 * 每個條目固定佔一整行（`  "key": "value",`），排序只搬動整行，不解析或改寫 value 內容與跳脫字元。
 * 純文字轉換，不特定於任何平台的語系檔格式。輸入可以是 CRLF 或 LF 換行，輸出一律使用 LF。
 * 呼叫端需提供符合上述格式的內容；此函式只檢查首尾行與結尾換行，不驗證每筆條目或 JSON 語法。
 *
 * @param original 要排序的語系檔內容。
 * @return 依 key 字典序排列且使用 LF 換行的內容。
 * @throws IllegalArgumentException [original] 首行不是左大括號，或末尾不是右大括號加換行時拋出。
 */
internal fun sortedLangFileText(original: String): String {
    val lines = original.withLfLineEndings().split("\n")
    require(lines.firstOrNull() == "{" && lines.getOrNull(lines.size - 2) == "}" && lines.lastOrNull() == "") {
        "Language file does not match the expected single-entry-per-line format"
    }
    val entries = lines.subList(1, lines.size - 2).map { it.removeSuffix(",") }
    val sortedEntries = entries.sortedBy { line -> line.substringAfter('"').substringBefore('"') }
    val body = sortedEntries.mapIndexed { index, line -> if (index == sortedEntries.lastIndex) line else "$line," }
    return (listOf("{") + body + listOf("}", "")).joinToString("\n")
}

/** 將 CRLF 換行統一為 LF。 */
private fun String.withLfLineEndings(): String = replace("\r\n", "\n")

/**
 * 合併目前載入的 project、catalog 模組與非 Gradle 平台索引。
 *
 * @param project 用於取得 root、已載入專案及平台 catalog 的 project。
 * @return 所有應存在 README.md 的檔案路徑集合。
 */
internal fun expectedModuleReadmes(project: Project): Set<File> {
    val root = project.rootDir
    val catalog = root.resolve("gradle/platform-targets.toml")
    val catalogModules = if (catalog.isFile) {
        PlatformTargetCatalog.load(catalog, root).flatMap(PlatformTarget::modules)
    } else {
        emptyList()
    }
    val directories = buildSet {
        add(root)
        project.rootProject.allprojects.mapTo(this) { it.projectDir }
        catalogModules.mapTo(this) { root.resolve(it) }
        if (catalog.isFile) {
            PlatformTargetCatalog.load(catalog, root)
                .flatMap(PlatformTarget::exampleModules)
                .mapTo(this) { root.resolve(it) }
        }
        add(root.resolve("build-logic"))
        add(root.resolve("platform"))
        add(root.resolve("platform/minecraft"))
        add(root.resolve("example"))
        add(root.resolve("platform/minecraft/example"))
    }
    return directories.mapTo(linkedSetOf()) { it.resolve("README.md") }
}
