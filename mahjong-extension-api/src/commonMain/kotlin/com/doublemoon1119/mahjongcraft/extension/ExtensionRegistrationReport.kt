package com.doublemoon1119.mahjongcraft.extension

/** 單一 extension registry 類別中的第三方登記結果。 */
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

/** Extension 初始化後可供平台輸出的完整第三方登記報告。 */
data class ExtensionRegistrationReport(
    val extensionIds: List<String>,
    val categories: List<ExtensionRegistrationCategory>,
) {
    init {
        require(extensionIds == extensionIds.distinct().sorted()) {
            "Extension IDs must be distinct and sorted"
        }
    }

    /** 報告內 extension 與 registry registration 的總筆數。 */
    val registrationCount: Int get() = extensionIds.size + categories.sumOf { it.registrationIds.size }

    /** 報告內非空類別總數，包含 extension 本身。 */
    val categoryCount: Int get() = (if (extensionIds.isEmpty()) 0 else 1) + categories.count { it.registrationIds.isNotEmpty() }

    /** 判斷指定每類上限是否會截斷任何輸出。 */
    fun isTruncatedAt(maxIdsPerCategory: Int): Boolean = extensionIds.size > maxIdsPerCategory ||
        categories.any { it.registrationIds.size > maxIdsPerCategory }
}

/** 將第三方 extension 登記報告格式化成穩定的樹狀純文字。 */
object ExtensionRegistrationReportFormatter {
    /** INFO 預設每類最多顯示的 registration ID 數量。 */
    const val DEFAULT_MAX_IDS_PER_CATEGORY: Int = 8

    /** 建立樹狀報告；[maxIdsPerCategory] 為每個類別的顯示上限。 */
    fun format(
        report: ExtensionRegistrationReport,
        maxIdsPerCategory: Int = DEFAULT_MAX_IDS_PER_CATEGORY,
    ): String {
        require(maxIdsPerCategory > 0) { "Maximum IDs per category must be positive" }
        if (report.extensionIds.isEmpty()) return "Loaded 0 third-party extensions."

        val categories = buildList {
            add("Mahjong Extension" to report.extensionIds.distinct().sorted())
            report.categories.filter { it.registrationIds.isNotEmpty() }.forEach { category ->
                add(category.displayName to category.registrationIds)
            }
        }
        return buildString {
            append(
                "Loaded ${report.registrationCount} third-party registration(s) across " +
                    "${report.categoryCount} categories from ${report.extensionIds.size} extension(s):",
            )
            categories.forEachIndexed { index, (displayName, ids) ->
                append('\n')
                append(if (index == categories.lastIndex) "└── " else "├── ")
                append(displayName)
                append(" (${ids.size}): ")
                append(formatIds(ids, maxIdsPerCategory))
            }
        }
    }

    /** 將單一類別的 ID 清單格式化並依上限補上省略數量。 */
    private fun formatIds(ids: List<String>, maxIds: Int): String {
        val visible = ids.take(maxIds)
        val remaining = ids.size - visible.size
        return buildString {
            append('[')
            append(visible.joinToString())
            if (remaining > 0) append(", … (+$remaining more)")
            append(']')
        }
    }
}

/** 保存第三方 callback 執行前，各診斷類別已存在的 registration key。 */
class ExtensionRegistrationSnapshot(
    private val categories: List<ExtensionRegistrationSnapshotCategory>,
) {
    /** 與 [current] 比較並回傳新增的非空第三方 registration 類別。 */
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
