package com.doublemoon1119.mahjongcraft.extension

/** 單一 registry 類別中，某個來源登記的項目。 */
data class ExtensionRegistrationCategory(
    val id: String,
    val displayName: String,
    val registrationIds: List<String>,
) {
    init {
        require(id.isNotBlank()) { "Registration category ID must not be blank" }
        require(displayName.isNotBlank()) { "Registration category display name must not be blank" }
        require(registrationIds == registrationIds.distinct().sorted()) {
            "Registration IDs must be distinct and sorted"
        }
    }
}

/**
 * 一個登記來源與它登記的所有項目。
 *
 * @property extensionId 登記這些項目的 extension ID；為 null 時代表不經 extension 的內建登記。
 * @property categories 這個來源登記的非空類別，依類別固定順序排列。
 */
data class ExtensionRegistrationSource(
    val extensionId: String?,
    val categories: List<ExtensionRegistrationCategory>,
) {
    init {
        require(categories.none { it.registrationIds.isEmpty() }) { "Registration categories must not be empty" }
    }

    /** 這個來源登記的項目總數。 */
    val registrationCount: Int get() = categories.sumOf { it.registrationIds.size }

    /** 報告中顯示的來源名稱。 */
    val displayName: String get() = extensionId ?: BUILT_IN_DISPLAY_NAME

    /** [ExtensionRegistrationSource] 的固定值。 */
    companion object {
        /** 內建登記在報告中顯示的來源名稱。 */
        const val BUILT_IN_DISPLAY_NAME: String = "built-in"
    }
}

/**
 * Extension 初始化後啟用的所有登記，依來源分組。
 *
 * @property sources 依登記順序排列的來源；同一個來源只出現一次。
 */
data class ExtensionRegistrationReport(
    val sources: List<ExtensionRegistrationSource>,
) {
    init {
        require(sources.map { it.extensionId }.distinct().size == sources.size) { "Registration sources must be distinct" }
    }

    /** 所有來源登記的項目總數。 */
    val registrationCount: Int get() = sources.sumOf { it.registrationCount }
}

/**
 * 依來源合併多份登記結果，例如核心與各平台分別登記的項目。
 *
 * 來源依第一次出現的順序排列；同一來源在多份結果中的類別依結果順序串接。
 */
fun mergeRegistrationSources(vararg sourceLists: List<ExtensionRegistrationSource>): List<ExtensionRegistrationSource> {
    val categoriesBySource = linkedMapOf<String?, MutableList<ExtensionRegistrationCategory>>()
    sourceLists.forEach { sources ->
        sources.forEach { source -> categoriesBySource.getOrPut(source.extensionId) { mutableListOf() } += source.categories }
    }
    return categoriesBySource.map { (extensionId, categories) -> ExtensionRegistrationSource(extensionId, categories) }
}

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

/** 保存各診斷類別在某一時間點已存在的 registration key。 */
class ExtensionRegistrationSnapshot(
    private val categories: List<ExtensionRegistrationSnapshotCategory>,
) {
    /** 快照中所有非空類別。 */
    fun toCategories(): List<ExtensionRegistrationCategory> = categories.mapNotNull { category ->
        category.registrationKeys.takeIf(Set<String>::isNotEmpty)?.let {
            ExtensionRegistrationCategory(category.id, category.displayName, it.sorted())
        }
    }

    /** 與 [current] 比較並回傳新增的非空類別。 */
    fun additionsSince(current: ExtensionRegistrationSnapshot): List<ExtensionRegistrationCategory> {
        val baselineById = categories.associateBy(ExtensionRegistrationSnapshotCategory::id)
        return current.categories.mapNotNull { category ->
            val additions = category.registrationKeys - baselineById[category.id].orEmptyKeys()
            additions.takeIf(Set<String>::isNotEmpty)?.let {
                ExtensionRegistrationCategory(category.id, category.displayName, it.sorted())
            }
        }
    }
}

/** 單一診斷類別在某一時間點的不可變 registration key 集合。 */
data class ExtensionRegistrationSnapshotCategory(
    val id: String,
    val displayName: String,
    val registrationKeys: Set<String>,
)

/** 取得可能不存在的 baseline 類別 key。 */
private fun ExtensionRegistrationSnapshotCategory?.orEmptyKeys(): Set<String> = this?.registrationKeys.orEmpty()
