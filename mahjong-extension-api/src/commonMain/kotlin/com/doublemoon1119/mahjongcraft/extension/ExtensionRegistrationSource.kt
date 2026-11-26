package com.doublemoon1119.mahjongcraft.extension

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
