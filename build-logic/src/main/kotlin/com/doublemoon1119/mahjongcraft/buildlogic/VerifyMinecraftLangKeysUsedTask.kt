package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * 驗證 Minecraft 語系檔中的每個 key 都有正式程式碼或資源使用。
 *
 * 翻譯 key 一律以完整字串寫在程式碼或資源中，因此只要比對正式原始碼與資源裡出現過的字串即可判斷；
 * Minecraft 依註冊 ID 自動組成的名稱（例如 `item.<namespace>.<path>`）改為確認註冊 ID 的路徑有出現。
 */
abstract class VerifyMinecraftLangKeysUsedTask : DefaultTask() {
    /** repository 根目錄；在其下搜尋正式原始碼與資源。 */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    /** 要檢查的語系檔目錄，路徑形如 `assets/<namespace>/lang`。 */
    @get:Internal
    abstract val langDirectories: ListProperty<File>

    /** 收集正式程式碼與資源中的字串，回報沒有被使用的語系 key。 */
    @TaskAction
    fun verifyUsage() {
        val root = rootDirectory.get().asFile
        val literals = productionSourceFiles(root).flatMapTo(mutableSetOf()) { stringLiterals(it.readText()) }
        val violations = langDirectories.get().flatMap { directory ->
            val namespace = directory.parentFile.name
            val keys = minecraftLangFiles(directory).flatMapTo(sortedSetOf()) { langFileKeys(it.readText()) }
            unusedLangKeys(keys, literals, namespace).map { "${directory.relativeTo(root).invariantSeparatorsPath}: $it" }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Language keys are not used by any production source or resource (remove them or reference them in full):\n" +
                    violations.joinToString("\n") { "  - $it" },
            )
        }
    }
}

/** Minecraft 依註冊 ID 組成名稱的 key 類別，格式為 `<類別>.<namespace>.<path>`。 */
private val REGISTRY_NAME_CATEGORIES: Set<String> = setOf("item", "block", "entity", "itemGroup", "stat")

/** 搜尋原始碼時略過的目錄名稱。 */
private val SKIPPED_DIRECTORY_NAMES: Set<String> = setOf("build", ".gradle", ".git", ".idea", "docs", "lang")

/** 會引用翻譯 key 的檔案副檔名。 */
private val SOURCE_EXTENSIONS: Set<String> = setOf("kt", "json", "mcmeta")

/**
 * 列出 repository 中所有正式 source set（`main`、`commonMain`、`jvmMain` 等）的原始碼與資源檔，不含語系檔。
 *
 * @param root repository 根目錄。
 * @return 依路徑排序的檔案。
 */
internal fun productionSourceFiles(root: File): List<File> = root.walkTopDown()
    .onEnter { it == root || it.name !in SKIPPED_DIRECTORY_NAMES }
    .filter { file -> file.isFile && file.extension in SOURCE_EXTENSIONS && isInProductionSourceSet(file, root) }
    .sortedBy { it.path }
    .toList()

/**
 * 判斷檔案是否位於正式 source set：路徑中 `src` 的下一層名稱是 `main` 或以 `Main` 結尾。
 *
 * @param file 要判斷的檔案。
 * @param root repository 根目錄。
 * @return 位於正式 source set 時為 true。
 */
private fun isInProductionSourceSet(file: File, root: File): Boolean {
    val segments = file.relativeTo(root).invariantSeparatorsPath.split('/')
    return segments.zipWithNext().any { (parent, child) -> parent == "src" && (child == "main" || child.endsWith("Main")) }
}

/**
 * 取出文字中所有雙引號字串的內容，跳脫字元保持原樣。
 *
 * @param text Kotlin 或 JSON 原始碼。
 * @return 字串內容的集合。
 */
internal fun stringLiterals(text: String): Set<String> = STRING_LITERAL.findAll(text).mapTo(mutableSetOf()) { it.groupValues[1] }

/** 雙引號字串，允許跳脫字元。 */
private val STRING_LITERAL: Regex = Regex("\"((?:[^\"\\\\\\n]|\\\\.)*)\"")

/**
 * 取出語系檔的所有 key；語系檔每個條目固定佔一行。
 *
 * @param text 語系檔內容。
 * @return 依出現順序排列的 key。
 */
internal fun langFileKeys(text: String): List<String> = text.lineSequence()
    .map(String::trim)
    .filter { it.startsWith("\"") }
    .map { it.substringAfter('"').substringBefore('"') }
    .toList()

/**
 * 找出沒有被任何字串引用的語系 key。
 *
 * 一般 key 必須以完整字串出現；Minecraft 依註冊 ID 組成的名稱只要求註冊 ID 的路徑（或完整 ID）以字串出現。
 *
 * @param keys 語系檔中的 key。
 * @param literals 正式原始碼與資源中的字串內容。
 * @param namespace 語系檔所屬的 namespace。
 * @return 沒有被使用的 key，依字典序排列。
 */
internal fun unusedLangKeys(keys: Collection<String>, literals: Set<String>, namespace: String): List<String> = keys
    .filterNot { key ->
        val category = key.substringBefore('.')
        val registryPath = key.removePrefix("$category.$namespace.").takeIf { category in REGISTRY_NAME_CATEGORIES && it != key }
        key in literals || (registryPath != null && (registryPath in literals || "$namespace:$registryPath" in literals))
    }
    .sorted()
