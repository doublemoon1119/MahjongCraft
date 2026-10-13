package com.doublemoon1119.mahjongcraft.extension

/**
 * 保存各診斷類別在某一時間點已存在的 registration key。
 *
 * @param categories 用於列出類別及比較前後 key 差異的快照資料。
 */
class ExtensionRegistrationSnapshot(
    categories: List<ExtensionRegistrationSnapshotCategory>,
) {
    /** 與呼叫端集合分離的分類及註冊 key 快照。 */
    private val categories: List<ExtensionRegistrationSnapshotCategory> = categories.map { category ->
        category.copy(registrationKeys = category.registrationKeys.toSet())
    }

    /**
     * 轉換為登記分類，略過沒有 key 的類別，並將各類別的 key 依字串排序。
     *
     * @return 快照中所有非空的登記分類。
     */
    fun toCategories(): List<ExtensionRegistrationCategory> = categories.mapNotNull { category ->
        category.registrationKeys.takeIf(Set<String>::isNotEmpty)?.let {
            ExtensionRegistrationCategory(category.id, category.displayName, it.sorted())
        }
    }

    /**
     * 與 [current] 比較並回傳各類別新增的 key；沒有新增 key 的類別不會出現在結果中。
     *
     * @param current 要與此快照比較的較新快照。
     * @return 僅含新增 key 的分類；沒有新增 key 的分類會省略，分類內 key 依字串排序。
     */
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
