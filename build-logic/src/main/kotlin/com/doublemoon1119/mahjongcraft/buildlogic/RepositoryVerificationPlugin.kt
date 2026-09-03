package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/** 在 root project 集中註冊 repository 層級的靜態驗證。 */
class RepositoryVerificationPlugin : Plugin<Project> {
    /** 註冊驗證任務並統一串接至 root `check`。 */
    override fun apply(project: Project) {
        require(project == project.rootProject) { "mahjongcraft.repository-verification can only be applied to the root project" }
        project.pluginManager.apply("base")
        val verifyNoDocsTempReferences = project.tasks.register(
            "verifyNoDocsTempReferences",
            VerifyNoDocsTempReferencesTask::class.java,
        ) {
            group = "verification"
            description = "Fails if any Kotlin source references a file under the gitignored docs/temp directory."
        }
        val verifyProjectVersionPolicy = project.tasks.register(
            "verifyProjectVersionPolicy",
            VerifyProjectVersionPolicyTask::class.java,
        ) {
            group = "verification"
            description = "Verifies every loaded project uses its declared MahjongCraft release-train version."
        }
        val verifyModuleReadmes = project.tasks.register("verifyModuleReadmes", VerifyModuleReadmesTask::class.java) {
            group = "verification"
            description = "Verifies that every module and platform index has an exact README.md file."
        }
        project.tasks.register("sortMinecraftLangFiles", SortMinecraftLangFilesTask::class.java) {
            group = "formatting"
            description = "Sorts MahjongCraft Minecraft language files by translation key."
        }
        val verifyMinecraftLangFileKeyOrder = project.tasks.register(
            "verifyMinecraftLangFileKeyOrder",
            VerifyMinecraftLangFileKeyOrderTask::class.java,
        ) {
            group = "verification"
            description = "Verifies MahjongCraft Minecraft language files are sorted by translation key."
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

/** 阻擋原始碼註解引用不進版控的 `docs/temp` 檔案。 */
abstract class VerifyNoDocsTempReferencesTask : DefaultTask() {
    /** 掃描 Kotlin 原始碼並回報引用。 */
    @TaskAction
    fun verifyReferences() {
        val docsTemp = project.rootDir.resolve("docs/temp")
        if (!docsTemp.exists()) return
        val gitOutput = ProcessBuilder("git", "ls-files", "--others", "--ignored", "--exclude-standard", "--", "docs/temp")
            .directory(project.rootDir)
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
        project.rootProject.allprojects.forEach { candidate ->
            candidate.projectDir.resolve("src").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { source ->
                    val text = source.readText()
                    ignoredFileNames.filter(text::contains).forEach { name ->
                        violations += "${source.relativeTo(project.rootDir)} references docs/temp file: $name"
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
    /** 比對 project path、平台目錄與 version catalog。 */
    @TaskAction
    fun verifyVersions() {
        val versions = project.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
        val minecraftVersion = versions.findVersion("minecraft-mod-version").get().requiredVersion
        val logicVersion = versions.findVersion("logic-version").get().requiredVersion
        val flowVersion = versions.findVersion("flow-version").get().requiredVersion
        val aiVersion = versions.findVersion("ai-version").get().requiredVersion
        val extensionApiVersion = versions.findVersion("extension-api-version").get().requiredVersion
        val violations = project.rootProject.allprojects.mapNotNull { candidate ->
            val expected = when {
                candidate.path == ":" -> "0.0.0-dev"
                candidate.path == ":mahjong-logic" -> logicVersion
                candidate.path == ":mahjong-ai" -> aiVersion
                candidate.path == ":mahjong-extension-api" -> extensionApiVersion
                candidate.path == ":mahjong-flow" || candidate.path.startsWith(":mahjong-flow:") -> flowVersion
                candidate.path == ":testing" || candidate.path.startsWith(":testing:") -> "0.0.0-dev"
                candidate.projectDir.toPath().startsWith(project.rootDir.resolve("platform/minecraft").toPath()) -> minecraftVersion
                else -> return@mapNotNull "${candidate.path} has no version policy"
            }
            if (candidate.version.toString() == expected) null else "${candidate.path}: expected $expected, found ${candidate.version}"
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Project version policy violations:\n" + violations.joinToString("\n") { "  - $it" })
        }
    }
}

/** 驗證所有模組與平台索引都具備人工維護的 `README.md`。 */
abstract class VerifyModuleReadmesTask : DefaultTask() {
    /** 供驗證使用的 README 集合。 */
    @get:Internal
    val expectedReadmes: Set<File>
        get() = expectedModuleReadmes(project)

    /** 一次列出所有缺少的 README。 */
    @TaskAction
    fun verifyReadmes() {
        val missing = expectedReadmes.filterNot(::hasExactReadmeName)
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing required README.md files:\n" +
                    missing.joinToString("\n") { "  - ${it.relativeTo(project.rootDir).invariantSeparatorsPath}" },
            )
        }
    }
}

/** 依 key 字典序重新排列所有 Minecraft 語系檔。 */
abstract class SortMinecraftLangFilesTask : DefaultTask() {
    /** 逐檔改寫排序後的內容。 */
    @TaskAction
    fun sort() {
        minecraftLangFiles(project).forEach { file -> file.writeText(sortedLangFileText(file.readText())) }
    }
}

/** 驗證所有 Minecraft 語系檔已依 key 字典序排列。 */
abstract class VerifyMinecraftLangFileKeyOrderTask : DefaultTask() {
    /** 逐檔比對排序前後內容，回報未排序的檔案。 */
    @TaskAction
    fun verifyOrder() {
        val violations = minecraftLangFiles(project).filter { it.readText() != sortedLangFileText(it.readText()) }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Language files are not sorted by translation key (run ./gradlew sortMinecraftLangFiles to fix):\n" +
                    violations.joinToString("\n") { "  - ${it.relativeTo(project.rootDir).invariantSeparatorsPath}" },
            )
        }
    }
}

/** Minecraft 平台的語系檔目錄；模組未載入時目錄仍在磁碟上，不受目前 target 選擇影響。 */
internal fun minecraftLangFiles(project: Project): List<File> {
    val dir = project.rootDir.resolve("platform/minecraft/common/src/jvmMain/resources/assets/mahjongcraft/lang")
    if (!dir.isDirectory) return emptyList()
    return dir.listFiles { file -> file.isFile && file.extension == "json" }
        ?.sortedBy { it.name }
        ?: emptyList()
}

/**
 * 將語系檔內容依 key 字典序重排。
 *
 * 每個條目固定佔一整行（`  "key": "value",`），排序只搬動整行，不解析或改寫 value 內容與跳脫字元。
 * 純文字轉換，不特定於任何平台的語系檔格式。
 *
 * @throws IllegalArgumentException [original] 不符合這個固定的單行條目格式。
 */
internal fun sortedLangFileText(original: String): String {
    val lines = original.split("\n")
    require(lines.firstOrNull() == "{" && lines.getOrNull(lines.size - 2) == "}" && lines.lastOrNull() == "") {
        "Language file does not match the expected single-entry-per-line format"
    }
    val entries = lines.subList(1, lines.size - 2).map { it.removeSuffix(",") }
    val sortedEntries = entries.sortedBy { line -> line.substringAfter('"').substringBefore('"') }
    val body = sortedEntries.mapIndexed { index, line -> if (index == sortedEntries.lastIndex) line else "$line," }
    return (listOf("{") + body + listOf("}", "")).joinToString("\n")
}

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
