package com.doublemoon1119.mahjongcraft.buildlogic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * 總任務 `autotest` 的總報告。
 *
 * - 讀取這次執行期間各目標寫出的最新報告，彙整成一份 Markdown。
 * - 只採用在 [startFile] 記下的時間之後開始的報告，較舊的記為沒有報告。
 * - 任何報告不是通過，或沒有報告時，讓建置失敗。
 * - 總報告寫在 [outputRoot] 底下以開始時間命名的資料夾，只保留最近 [keep] 份。
 *
 * @property reportRoots 報告名稱對報告根資料夾（裡面有 `latest.txt`）的絕對路徑。
 * @property startFile 總任務開始時記下開始時間的檔案。
 * @property outputRoot 總報告的根資料夾。
 * @property keep 保留最近幾份總報告。
 */
@DisableCachingByDefault(because = "Summarizes reports written by game runs")
abstract class AutotestSummaryTask : DefaultTask() {
    @get:Internal
    abstract val reportRoots: MapProperty<String, String>

    @get:Internal
    abstract val startFile: RegularFileProperty

    @get:Internal
    abstract val outputRoot: DirectoryProperty

    @get:Internal
    abstract val keep: Property<Int>

    /** 寫出總報告。有任何報告不是通過，或沒有報告時，讓建置失敗。 */
    @TaskAction
    fun summarize() {
        val startedAt = startFile.get().asFile.readText().trim()
        val results = reportRoots.get().toSortedMap().map { (label, root) -> AutotestSummary.read(label, File(root), startedAt) }
        val root = outputRoot.get().asFile
        val output = root.resolve(startedAt).apply { mkdirs() }
        val summary = output.resolve("summary.md")
        summary.writeText(AutotestSummary.markdown(startedAt, results))
        root.listFiles().orEmpty().filter { it.isDirectory && AutotestSummary.isReportFolder(it.name) }
            .sortedByDescending { it.name }
            .drop(keep.get())
            .forEach(File::deleteRecursively)
        logger.lifecycle("Automated test summary: {}", summary.absolutePath)
        val failed = results.filter { it.status != PASSED }
        if (failed.isNotEmpty()) {
            throw GradleException("Automated tests did not pass: ${failed.joinToString { "${it.label} ${it.status}" }}. See ${summary.absolutePath}")
        }
    }

    private companion object {
        /** 報告通過的結果。 */
        const val PASSED = "passed"
    }
}

/**
 * 總報告中的一份報告。
 *
 * @property label 報告名稱，例如 `minecraft_v1.20.1_fabric client`。
 * @property status 結果：報告的整體結果，或沒有這次的報告時為 `missing`。
 * @property report 報告資料夾；沒有這次的報告時為 null。
 * @property notes 報告中的提醒事項。
 * @property failures 未通過的步驟與檢查。
 */
internal data class AutotestSummaryEntry(
    val label: String,
    val status: String,
    val report: File?,
    val notes: List<String>,
    val failures: List<String>,
)

/** 讀取各目標的報告並寫成總報告。 */
internal object AutotestSummary {
    /** 報告與總報告資料夾名稱的格式，與開始時間相同。 */
    private val FOLDER_NAME = Regex("""\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}""")

    /** 沒有這次報告時的結果。 */
    const val MISSING: String = "missing"

    /**
     * 名稱是否是報告時間格式的資料夾。
     *
     * @param name 資料夾名稱。
     * @return 符合時為 true。
     */
    fun isReportFolder(name: String): Boolean = FOLDER_NAME.matches(name)

    /**
     * 讀取一個目標最新的報告。不是在 [startedAt] 之後開始的報告，當作沒有報告。
     *
     * @param label 報告名稱。
     * @param root 報告根資料夾，裡面有 `latest.txt`。
     * @param startedAt 總任務開始的時間，與報告資料夾名稱相同的格式。
     * @return 總報告中的一份報告。
     */
    fun read(label: String, root: File, startedAt: String): AutotestSummaryEntry {
        val folderName = root.resolve("latest.txt").takeIf(File::isFile)?.readText()?.trim()
        val folder = folderName?.takeIf { isReportFolder(it) && it >= startedAt }?.let(root::resolve)
        val reportFile = folder?.resolve("report.json")?.takeIf(File::isFile)
            ?: return AutotestSummaryEntry(label, MISSING, null, emptyList(), listOf("No report written by this run in $root"))
        val report = Json.parseToJsonElement(reportFile.readText()).jsonObject
        val failures = report.getValue("steps").jsonArray.map { it.jsonObject }.flatMap { step ->
            val id = step.string("id")
            val stepStatus = step.string("status")
            val checks = step.getValue("checks").jsonArray.map { it.jsonObject }.filterNot { it.getValue("passed").jsonPrimitive.boolean }
                .map { check -> "$id: ${check.string("name")}" + (check["detail"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { " - $it" }.orEmpty() }
            when {
                checks.isNotEmpty() -> checks
                stepStatus == "failed" || stepStatus == "aborted" -> listOf("$id: $stepStatus")
                else -> emptyList()
            }
        }
        val notes = (report["notes"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }
        return AutotestSummaryEntry(label, report.string("status"), folder, notes, failures)
    }

    /**
     * 總報告的 Markdown：每份報告的結果與連結，接著列出提醒事項與未通過的檢查。
     *
     * @param startedAt 總任務開始的時間。
     * @param entries 各份報告。
     * @return Markdown 文字。
     */
    fun markdown(startedAt: String, entries: List<AutotestSummaryEntry>): String = buildString {
        appendLine("# Automated test summary")
        appendLine()
        appendLine("- Started: $startedAt")
        appendLine("- Result: ${if (entries.all { it.status == "passed" }) "PASSED" else "FAILED"}")
        appendLine()
        entries.forEach { entry ->
            val link = entry.report?.resolve("report.md")?.let { " ([report](${it.toURI()}))" }.orEmpty()
            appendLine("- ${entry.status.uppercase()} ${entry.label}$link")
        }
        entries.flatMap { it.notes }.distinct().takeIf { it.isNotEmpty() }?.let { notes ->
            appendLine()
            appendLine("## Notes")
            appendLine()
            notes.forEach { appendLine("- $it") }
        }
        entries.filter { it.failures.isNotEmpty() }.forEach { entry ->
            appendLine()
            appendLine("## ${entry.label}: ${entry.status.uppercase()}")
            appendLine()
            entry.failures.forEach { appendLine("- [ ] $it") }
        }
    }

    /**
     * 物件中的文字欄位。
     *
     * @receiver JSON 物件。
     * @param key 欄位名稱。
     * @return 欄位的文字。
     */
    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
}
