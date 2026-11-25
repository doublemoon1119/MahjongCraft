package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * 驗證專案使用的版本不高於任何建置目標執行時提供的版本。
 *
 * 執行環境提供、建置產物不附帶的函式庫只會有一份。版本高於它時，程式在執行時會找不到較新的 API。
 */
abstract class VerifyProvidedVersionsTask : DefaultTask() {
    /** `libs` version catalog 中的 Kotlin API 版本上限。 */
    @get:Input
    abstract val kotlinApiVersion: Property<String>

    /** 建置目標提到的函式庫在 `libs` version catalog 中的版本。catalog 沒有的名稱不在其中。 */
    @get:Input
    abstract val catalogVersions: MapProperty<String, String>

    /** 依 target ID 索引的執行環境提供版本。 */
    @get:Input
    abstract val providedVersions: MapProperty<String, Map<String, String>>

    /** 比對版本並列出所有違規。 */
    @TaskAction
    fun verifyVersions() {
        val violations = findProvidedVersionViolations(kotlinApiVersion.get(), catalogVersions.get(), providedVersions.get())
        if (violations.isNotEmpty()) {
            throw GradleException("Versions exceed what platform targets provide:\n" + violations.joinToString("\n") { "  - $it" })
        }
    }
}

/**
 * 找出高於建置目標提供版本的設定。
 *
 * `kotlin` 比對的是 Kotlin API 版本上限而非編譯器版本，其他名稱比對 `libs` version catalog 中同名的版本。
 *
 * @param kotlinApiVersion Kotlin API 版本上限，例如 `2.4`。
 * @param catalogVersions `libs` version catalog 中的版本，key 為版本名稱。
 * @param providedVersions 依 target ID 索引的執行環境提供版本。
 * @return 每項違規的說明。沒有違規時為空。
 */
internal fun findProvidedVersionViolations(
    kotlinApiVersion: String,
    catalogVersions: Map<String, String>,
    providedVersions: Map<String, Map<String, String>>,
): List<String> = providedVersions.toSortedMap().flatMap { (targetId, provided) ->
    provided.toSortedMap().mapNotNull { (name, providedVersion) ->
        val (label, used) = if (name == KOTLIN_PROVIDED_VERSION_NAME) {
            KOTLIN_API_VERSION_ALIAS to kotlinApiVersion
        } else {
            name to (catalogVersions[name] ?: return@mapNotNull "$targetId provides $name, which is not a libs catalog version")
        }
        val comparison = compareNumericVersions(used, providedVersion)
            ?: return@mapNotNull "$targetId: cannot compare $label $used with provided $name $providedVersion"
        if (comparison > 0) "$targetId provides $name $providedVersion, lower than $label $used" else null
    }
}

/**
 * 逐段比較只含數字與點的版本，缺少的段落視為 0。
 *
 * @param first 第一個版本。
 * @param second 第二個版本。
 * @return 第一個版本較低時為負、相同時為 0、較高時為正。任一版本不是純數字版本時為 null。
 */
internal fun compareNumericVersions(first: String, second: String): Int? {
    if (!NUMERIC_VERSION.matches(first) || !NUMERIC_VERSION.matches(second)) return null
    val firstParts = first.split('.').map(String::toInt)
    val secondParts = second.split('.').map(String::toInt)
    return (0 until maxOf(firstParts.size, secondParts.size))
        .map { firstParts.getOrElse(it) { 0 }.compareTo(secondParts.getOrElse(it) { 0 }) }
        .firstOrNull { it != 0 }
        ?: 0
}

/** 建置目標提供版本中代表 Kotlin 標準函式庫的名稱。 */
internal const val KOTLIN_PROVIDED_VERSION_NAME: String = "kotlin"

/** 只含數字與點的版本格式。 */
private val NUMERIC_VERSION = Regex("""\d+(\.\d+)*""")
