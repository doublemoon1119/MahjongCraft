package com.doublemoon1119.mahjongcraft.extension

/**
 * 單一 registry 類別中，某個來源登記的項目。
 *
 * @property id 類別的穩定識別字串，不可為空白。
 * @property displayName 類別的顯示名稱，不可為空白。
 * @property registrationIds 此來源登記的識別字串，必須不重複並依字串順序排列。
 */
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
