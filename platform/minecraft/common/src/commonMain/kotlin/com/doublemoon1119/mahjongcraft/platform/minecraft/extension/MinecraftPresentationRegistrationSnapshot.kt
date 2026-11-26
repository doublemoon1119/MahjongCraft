package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

/**
 * 保存所有 Minecraft 呈現 registry 在單一時間點的註冊內容。
 *
 * @property categories 各 registry 類別及其註冊 key 的快照。
 * @param categories 建立快照時的 registry 分類。
 */
class MinecraftPresentationRegistrationSnapshot(
    categories: List<MinecraftPresentationRegistrationSnapshotCategory>,
) {
    val categories: List<MinecraftPresentationRegistrationSnapshotCategory> = categories.map {
        it.copy(registrationKeys = it.registrationKeys.toSet())
    }

    /**
     * 略過空分類，將各分類的註冊識別碼依字串排序後建立報告內容。
     *
     * @return 依原分類順序排列的非空登記分類。
     */
    fun toCategories(): List<MinecraftPresentationRegistrationCategory> = categories
        .filter { it.registrationKeys.isNotEmpty() }
        .map { MinecraftPresentationRegistrationCategory(it.id, it.displayName, it.registrationKeys.sorted()) }

    /**
     * 與 [current] 比較並回傳新增的註冊內容。
     *
     * @param current 較新的註冊快照。
     * @return 依分類列出的新增註冊識別碼。
     */
    fun additionsSince(current: MinecraftPresentationRegistrationSnapshot): List<MinecraftPresentationRegistrationCategory> {
        val baselineById = categories.associateBy { it.id }
        return current.toCategories().mapNotNull { category ->
            val baseline = baselineById[category.id]?.registrationKeys.orEmpty()
            val additions = category.registrationIds.filterNot(baseline::contains)
            additions.takeIf { it.isNotEmpty() }?.let {
                MinecraftPresentationRegistrationCategory(category.id, category.displayName, it)
            }
        }
    }
}
