package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

/**
 * 目錄解析結果，區分缺少來源與配置不匹配。
 *
 * 不將沒有目錄解釋為沒有役種，也不回退至其他規則的目錄。
 */
sealed interface RuleCatalogueResolution {
    /** 本地沒有該規則的目錄來源。 */
    data object MissingProvider : RuleCatalogueResolution

    /** 來源不支援指定的配置。 */
    data object UnsupportedConfig : RuleCatalogueResolution

    /**
     * 已解析的規則說明。
     *
     * @property catalogue 來源提供的目錄。
     * @property usesDefaultConfig 是否使用一般說明預設配置。
     */
    data class Available(
        val catalogue: RuleCatalogue,
        val usesDefaultConfig: Boolean,
    ) : RuleCatalogueResolution
}
