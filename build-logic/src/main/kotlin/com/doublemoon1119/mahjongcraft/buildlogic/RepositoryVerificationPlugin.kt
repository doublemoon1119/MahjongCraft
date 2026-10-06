package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * 在 root project 集中註冊 repository 層級的靜態驗證。
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
                verifyProjectVersionPolicy,
                verifyModuleReadmes,
                verifyMinecraftLangFileKeyOrder,
            )
        }
    }
}

/** Minecraft 平台語系檔目錄，相對於 root project。 */
private const val MINECRAFT_LANG_DIRECTORY: String = "platform/minecraft/common/src/jvmMain/resources/assets/mahjongcraft/lang"

/** 阻擋原始碼註解引用不進版控的 `docs/temp` 檔案。 */
abstract class VerifyNoDocsTempReferencesTask : DefaultTask() {
    /** repository 根目錄。 */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    /** 要掃描 `src` 的所有 project 目錄。 */
    @get:Internal
    abstract val projectDirectories: ListProperty<File>

    /** 掃描 Kotlin 原始碼並回報引用。 */
    @TaskAction
    fun verifyReferences() {
        val root = rootDirectory.get().asFile
        val docsTemp = root.resolve("docs/temp")
        if (!docsTemp.exists()) return
        val gitOutput = ProcessBuilder("git", "ls-files", "--others", "--ignored", "--exclude-standard", "--", "docs/temp")
            .directory(root)
            .redirectErrorStream(true)
            .start()
            .let { process ->
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                output
            }
        val ignoredFileNames = gitOutput.lineSequence()
            .map { File(it.trim()).name }
            .filter(String::isNotBlank)
            .toSet()
        if (ignoredFileNames.isEmpty()) return
        val violations = mutableListOf<String>()
        projectDirectories.get().forEach { projectDir ->
            projectDir.resolve("src").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { source ->
                    val text = source.readText()
                    ignoredFileNames.filter(text::contains).forEach { name ->
                        violations += "${source.relativeTo(root)} references docs/temp file: $name"
                    }
                }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Found references to gitignored docs/temp files in source comments " +
                    "(move the explanation into the comment itself instead of citing the file):\n" +
                    violations.joinToString("\n") { "  - $it" },
            )
        }
    }
}

/** 驗證所有載入的 project 都明確使用其 release train 版本。 */
abstract class VerifyProjectVersionPolicyTask : DefaultTask() {
    /** repository 根目錄。 */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    /** 依 project path 索引的實際版本。 */
    @get:Internal
    abstract val projectVersions: MapProperty<String, String>

    /** 依 project path 索引的 project 目錄。 */
    @get:Internal
    abstract val projectDirectories: MapProperty<String, File>

    /** version catalog 中各 release train 的版本，key 為 catalog 的版本名稱。 */
    @get:Internal
    abstract val releaseTrainVersions: MapProperty<String, String>

    /** 比對 project path、平台目錄與 version catalog。 */
    @TaskAction
    fun verifyVersions() {
        val versions = releaseTrainVersions.get()
        val minecraftDirectory = rootDirectory.get().asFile.resolve("platform/minecraft").toPath()
        val directories = projectDirectories.get()
        val violations = projectVersions.get().mapNotNull { (path, actual) ->
            val expected = when {
                path == ":" -> "0.0.0-dev"
                path == ":mahjong-logic" -> versions.getValue("logic-version")
                path == ":mahjong-ai" -> versions.getValue("ai-version")
                path == ":mahjong-extension-api" -> versions.getValue("extension-api-version")
                path == ":mahjong-bundled-extensions" -> versions.getValue("bundled-extensions-version")
                path == ":mahjong-flow" || path.startsWith(":mahjong-flow:") -> versions.getValue("flow-version")
                path == ":testing" || path.startsWith(":testing:") -> "0.0.0-dev"
                directories.getValue(path).toPath().startsWith(minecraftDirectory) -> versions.getValue("minecraft-mod-version")
                else -> return@mapNotNull "$path has no version policy"
            }
            if (actual == expected) null else "$path: expected $expected, found $actual"
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Project version policy violations:\n" + violations.joinToString("\n") { "  - $it" })
        }
    }
}

/** version policy 使用的 release train 版本名稱。 */
private val RELEASE_TRAIN_VERSION_NAMES: List<String> = listOf(
    "minecraft-mod-version",
    "logic-version",
    "flow-version",
    "ai-version",
    "extension-api-version",
    "bundled-extensions-version",
)

/** 從 `libs` version catalog 讀取所有 release train 版本。 */
private fun releaseTrainVersions(project: Project): Map<String, String> {
    val catalog = project.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
    return RELEASE_TRAIN_VERSION_NAMES.associateWith { name -> catalog.findVersion(name).get().requiredVersion }
}

/** 驗證所有模組與平台索引都具備人工維護的 `README.md`。 */
abstract class VerifyModuleReadmesTask : DefaultTask() {
    /** repository 根目錄。 */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    /** 供驗證使用的 README 集合。 */
    @get:Internal
    abstract val expectedReadmes: SetProperty<File>

    /** 一次列出所有缺少的 README。 */
    @TaskAction
    fun verifyReadmes() {
        val root = rootDirectory.get().asFile
        val missing = expectedReadmes.get().filterNot(::hasExactReadmeName)
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing required README.md files:\n" +
                    missing.joinToString("\n") { "  - ${it.relativeTo(root).invariantSeparatorsPath}" },
            )
        }
    }
}

/** 依 key 字典序重新排列所有 Minecraft 語系檔。 */
abstract class SortMinecraftLangFilesTask : DefaultTask() {
    /** Minecraft 平台語系檔目錄。 */
    @get:Internal
    abstract val langDirectory: DirectoryProperty

    /** 逐檔改寫排序後的內容。 */
    @TaskAction
    fun sort() {
        minecraftLangFiles(langDirectory.get().asFile).forEach { file -> file.writeText(sortedLangFileText(file.readText())) }
    }
}

/** 驗證所有 Minecraft 語系檔已依 key 字典序排列。 */
abstract class VerifyMinecraftLangFileKeyOrderTask : DefaultTask() {
    /** repository 根目錄。 */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    /** Minecraft 平台語系檔目錄。 */
    @get:Internal
    abstract val langDirectory: DirectoryProperty

    /** 逐檔比對排序前後內容，回報未排序的檔案。 */
    @TaskAction
    fun verifyOrder() {
        val root = rootDirectory.get().asFile
        val violations = minecraftLangFiles(langDirectory.get().asFile).filterNot { isLangFileSorted(it.readText()) }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Language files are not sorted by translation key (run ./gradlew sortMinecraftLangFiles to fix):\n" +
                    violations.joinToString("\n") { "  - ${it.relativeTo(root).invariantSeparatorsPath}" },
            )
        }
    }
}

/** 目錄中的所有語系檔，依檔名排序；模組未載入時目錄仍在磁碟上，不受目前 target 選擇影響。 */
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
 * @throws IllegalArgumentException [text] 不符合 [sortedLangFileText] 要求的固定單行條目格式。
 */
internal fun isLangFileSorted(text: String): Boolean = text.withLfLineEndings() == sortedLangFileText(text)

/**
 * 將語系檔內容依 key 字典序重排。
 *
 * 每個條目固定佔一整行（`  "key": "value",`），排序只搬動整行，不解析或改寫 value 內容與跳脫字元。
 * 純文字轉換，不特定於任何平台的語系檔格式。輸入可以是 CRLF 或 LF 換行，輸出一律使用 LF。
 *
 * @throws IllegalArgumentException [original] 不符合這個固定的單行條目格式。
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

/** 即使在大小寫不敏感的檔案系統，也只接受精確的 `README.md` 名稱。 */
private fun hasExactReadmeName(readme: File): Boolean = readme.parentFile
    ?.listFiles()
    ?.any { it.isFile && it.name == "README.md" }
    ?: false

/** 合併目前載入的 project、catalog 模組與非 Gradle 平台索引。 */
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
        add(root.resolve("build-logic"))
        add(root.resolve("platform"))
        add(root.resolve("platform/minecraft"))
    }
    return directories.mapTo(linkedSetOf()) { it.resolve("README.md") }
}
