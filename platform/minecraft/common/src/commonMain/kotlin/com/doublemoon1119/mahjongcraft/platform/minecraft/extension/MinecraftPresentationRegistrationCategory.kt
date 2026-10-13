package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

/**
 * Minecraft 呈現註冊結果中的單一分類。
 *
 * @property id 分類的穩定識別碼。
 * @property displayName 分類的診斷名稱。
 * @property registrationIds 此來源新增的註冊識別碼，必須依字串排序且不重複。
 */
data class MinecraftPresentationRegistrationCategory(
    val id: String,
    val displayName: String,
    val registrationIds: List<String>,
) {
    init {
        require(id.isNotBlank()) { "Category ID must not be blank" }
        require(displayName.isNotBlank()) { "Category display name must not be blank" }
        require(registrationIds == registrationIds.distinct().sorted()) {
            "Registration IDs must be sorted and distinct"
        }
    }
}
