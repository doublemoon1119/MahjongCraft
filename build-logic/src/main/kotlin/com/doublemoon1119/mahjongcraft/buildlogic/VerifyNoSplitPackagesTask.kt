package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * 驗證 mod JAR 與它以 jar-in-jar 附帶的 JAR 之間沒有同名套件。
 *
 * Forge 以 Java 模組系統載入 mod 與附帶的函式庫，同一個套件出現在兩個 JAR 時拒絕啟動。這個檢查在建置時就找出這種情況。
 */
abstract class VerifyNoSplitPackagesTask : DefaultTask() {
    /** 要檢查的 mod JAR。 */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val modJar: RegularFileProperty

    /** 列出所有同名套件與它們所在的 JAR。 */
    @TaskAction
    fun verifyNoSplitPackages() {
        val split = findSplitPackages(modJar.get().asFile)
        if (split.isNotEmpty()) {
            throw GradleException(
                "Packages found in more than one JAR:\n" +
                    split.entries.joinToString("\n") { (pkg, jars) -> "  - $pkg: ${jars.joinToString()}" },
            )
        }
    }
}

/**
 * 找出 mod JAR 與 `META-INF/jarjar/` 下各 JAR 之間重複的套件。
 *
 * @param modJar 要檢查的 mod JAR。
 * @return 每個重複套件對應的 JAR 名稱，mod 本身以 `<mod>` 表示。沒有重複時為空。
 */
internal fun findSplitPackages(modJar: File): Map<String, List<String>> {
    val owners = sortedMapOf<String, MutableList<String>>()
    ZipFile(modJar).use { zip ->
        val entries = zip.entries().asSequence().toList()
        classPackages(entries.map { it.name }).forEach { owners.getOrPut(it) { mutableListOf() } += MOD_JAR_NAME }
        entries.filter { it.name.startsWith(JAR_IN_JAR_DIRECTORY) && it.name.endsWith(".jar") }.forEach { entry ->
            val names = ZipInputStream(zip.getInputStream(entry)).use { nested -> generateSequence { nested.nextEntry?.name }.toList() }
            val jarName = entry.name.substringAfterLast('/')
            classPackages(names).forEach { owners.getOrPut(it) { mutableListOf() } += jarName }
        }
    }
    return owners.filterValues { it.size > 1 }
}

/**
 * 取得 JAR 中含有類別的套件。`META-INF` 下的多版本類別不算。
 *
 * @param entryNames JAR 中的路徑。
 * @return 以 `.` 分隔的套件名稱。
 */
private fun classPackages(entryNames: List<String>): Set<String> = entryNames
    .filter { it.endsWith(".class") && !it.startsWith("META-INF/") && '/' in it }
    .mapTo(sortedSetOf()) { it.substringBeforeLast('/').replace('/', '.') }

/** jar-in-jar 附帶的 JAR 所在的資料夾。 */
private const val JAR_IN_JAR_DIRECTORY = "META-INF/jarjar/"

/** 報告中代表 mod JAR 本身的名稱。 */
private const val MOD_JAR_NAME = "<mod>"
