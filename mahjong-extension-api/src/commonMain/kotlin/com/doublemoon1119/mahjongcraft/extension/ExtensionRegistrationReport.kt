package com.doublemoon1119.mahjongcraft.extension

/**
 * Extension 初始化後啟用的所有登記，依來源分組。
 *
 * @property sources 依登記順序排列的來源；同一個來源只出現一次。
 */
data class ExtensionRegistrationReport(
    val sources: List<ExtensionRegistrationSource>,
) {
    init {
        require(sources.map { it.extensionId }.distinct().size == sources.size) { "Registration sources must be distinct" }
    }

    /** 所有來源登記的項目總數。 */
    val registrationCount: Int get() = sources.sumOf { it.registrationCount }
}

/**
 * 依來源合併多份登記結果，例如核心與各平台分別登記的項目。
 *
 * 來源依第一次出現的順序排列；同一來源在多份結果中的類別依結果順序串接。
 */
fun mergeRegistrationSources(vararg sourceLists: List<ExtensionRegistrationSource>): List<ExtensionRegistrationSource> {
    val categoriesBySource = linkedMapOf<String?, MutableList<ExtensionRegistrationCategory>>()
    sourceLists.forEach { sources ->
        sources.forEach { source -> categoriesBySource.getOrPut(source.extensionId) { mutableListOf() } += source.categories }
    }
    return categoriesBySource.map { (extensionId, categories) -> ExtensionRegistrationSource(extensionId, categories) }
}
