package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

/**
 * 保存所有 Minecraft 呈現 registry 在單一時間點的註冊內容。
 *
 * @property categories 各 registry 類別及其註冊 key 的快照。
 */
data class MinecraftPresentationRegistrationSnapshot(
    val categories: List<MinecraftPresentationRegistrationSnapshotCategory>,
) {
    /**
     * 與 [current] 比較並回傳新增的註冊內容。
     *
     * @param current 較新的註冊快照。
     * @return 依分類列出的新增註冊 key。
     */
    fun additionsSince(current: MinecraftPresentationRegistrationSnapshot): List<MinecraftPresentationRegistrationSnapshotCategory> {
        val baselineById = categories.associateBy { it.id }
        return current.categories.map { category ->
            category.copy(registrationKeys = category.registrationKeys - baselineById[category.id].orEmptyKeys())
        }
    }

    /** 將不存在的快照分類視為空 key 集合，供差異計算使用。 */
    private fun MinecraftPresentationRegistrationSnapshotCategory?.orEmptyKeys(): Set<String> = this?.registrationKeys.orEmpty()
}
