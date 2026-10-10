package com.doublemoon1119.mahjongcraft.extension

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

/** 取得可能不存在的 baseline 類別 key。 */
private fun ExtensionRegistrationSnapshotCategory?.orEmptyKeys(): Set<String> = this?.registrationKeys.orEmpty()
