package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

/**
 * 保存所有 Minecraft 呈現 registry 在單一時間點的註冊內容。
 *
 * @property categories 各 registry 類別及其註冊 key 的快照。
 */
data class MinecraftPresentationRegistrationSnapshot(
    val categories: List<MinecraftPresentationRegistrationSnapshotCategory>,
) {
    /** 與 [current] 比較並回傳新增的註冊內容。 */
    fun additionsSince(current: MinecraftPresentationRegistrationSnapshot): List<MinecraftPresentationRegistrationSnapshotCategory> {
        val baselineById = categories.associateBy { it.id }
        return current.categories.map { category ->
            category.copy(registrationKeys = category.registrationKeys - baselineById[category.id].orEmptyKeys())
        }
    }

    /** 取得 nullable 快照分類的註冊 key。 */
    private fun MinecraftPresentationRegistrationSnapshotCategory?.orEmptyKeys(): Set<String> = this?.registrationKeys.orEmpty()
}
