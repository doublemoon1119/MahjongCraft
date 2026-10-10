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
