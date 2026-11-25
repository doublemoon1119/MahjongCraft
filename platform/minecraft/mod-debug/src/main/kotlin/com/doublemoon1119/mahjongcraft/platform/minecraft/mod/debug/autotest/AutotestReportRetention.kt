package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 自動測試報告的保留規則。
 *
 * - 只保留最近幾次的報告資料夾，更舊的刪掉。
 * - 只處理報告根目錄底下、名稱符合報告時間格式的資料夾，其他檔案與資料夾不動。
 */
internal object AutotestReportRetention {
    /** 開發執行以這個系統屬性傳入要保留的報告次數。 */
    const val KEEP_PROPERTY: String = "mahjongcraft.autotest.keep"

    /** 沒有指定時保留的報告次數。 */
    const val DEFAULT_KEEP: Int = 10

    /** 報告資料夾名稱的時間格式。 */
    val FOLDER_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    /**
     * 讀取要保留的報告次數。
     *
     * @param value 系統屬性的值；沒有設定時為 null。
     * @return 要保留的次數。
     * @throws IllegalArgumentException 值不是正整數時拋出。
     */
    fun keepCount(value: String?): Int {
        if (value == null) return DEFAULT_KEEP
        val count = value.toIntOrNull()
        require(count != null && count >= 1) { "$KEEP_PROPERTY must be a positive integer: $value" }
        return count
    }

    /**
     * 刪掉超出保留次數的舊報告資料夾，依資料夾名稱的時間由新到舊保留。
     *
     * @param root 報告根目錄。
     * @param keep 要保留的次數，包含這次的報告。
     * @return 刪掉的資料夾。
     */
    fun prune(root: Path, keep: Int): List<Path> {
        require(keep >= 1) { "At least one report must be kept" }
        val base = root.toAbsolutePath().normalize()
        if (!Files.isDirectory(base)) return emptyList()
        val reports = Files.list(base).use { entries ->
            entries.filter { Files.isDirectory(it) && !Files.isSymbolicLink(it) && isReportFolder(it.fileName.toString()) }.toList()
        }
        val expired = reports.sortedByDescending { it.fileName.toString() }.drop(keep)
        expired.forEach { folder ->
            check(folder.toAbsolutePath().normalize().parent == base) { "Report folder is outside the report root: $folder" }
            Files.walk(folder).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
        return expired
    }

    /**
     * 名稱是否是報告資料夾的時間格式。
     *
     * @param name 資料夾名稱。
     * @return 符合格式時為 true。
     */
    private fun isReportFolder(name: String): Boolean = try {
        LocalDateTime.parse(name, FOLDER_FORMAT).format(FOLDER_FORMAT) == name
    } catch (_: DateTimeParseException) {
        false
    }
}
