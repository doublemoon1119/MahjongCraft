package com.doublemoon1119.mahjongcraft.extension

/** 將登記報告格式化成穩定的樹狀純文字。 */
object ExtensionRegistrationReportFormatter {
    /** 每個來源只列出登記筆數的摘要。 */
    fun formatSummary(report: ExtensionRegistrationReport): String = buildString {
        append(header(report))
        report.sources.forEachIndexed { index, source ->
            append('\n')
            append(branch(index, report.sources.lastIndex))
            append("${source.displayName} (${source.registrationCount})")
        }
    }

    /** 列出每個來源登記的所有類別與 ID。 */
    fun formatDetails(report: ExtensionRegistrationReport): String = buildString {
        append(header(report))
        report.sources.forEachIndexed { sourceIndex, source ->
            val isLastSource = sourceIndex == report.sources.lastIndex
            append('\n')
            append(branch(sourceIndex, report.sources.lastIndex))
            append("${source.displayName} (${source.registrationCount})")
            source.categories.forEachIndexed { categoryIndex, category ->
                append('\n')
                append(if (isLastSource) "    " else "│   ")
                append(branch(categoryIndex, source.categories.lastIndex))
                append("${category.displayName} (${category.registrationIds.size}): ")
                append(category.registrationIds.joinToString(prefix = "[", postfix = "]"))
            }
        }
    }

    /** 兩種格式共用的總覽行。 */
    private fun header(report: ExtensionRegistrationReport): String = "Enabled ${report.registrationCount} registration(s) from ${report.sources.size} source(s):"

    /** 樹狀清單中第 [index] 個項目的分支符號。 */
    private fun branch(index: Int, lastIndex: Int): String = if (index == lastIndex) "└── " else "├── "
}
