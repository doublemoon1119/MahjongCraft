package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/** 供內建與第三方規則登記目錄來源的凍結式 registry。 */
interface RuleCatalogueRegistry {
    /** 目前已登記規則 ID 的快照，供規則選擇與註冊統計使用。 */
    val registrationKeys: Set<String>

    /** 是否禁止後續註冊。 */
    val isFrozen: Boolean

    /**
     * 登記來源；拒絕重複規則 ID 與凍結後的變更。
     *
     * @param provider 規則自訂目錄來源。
     */
    fun register(provider: RuleCatalogueProvider)

    /**
     * 解析所選規則，不沿用其他規則的資料。
     *
     * @param ruleModuleId 所選規則模組 ID。
     * @param config 實際配置；null 明確表示使用一般說明的預設配置。
     * @return 可用目錄或明確的缺失原因；一般說明沒有實際設定可對照，條目不帶不適用原因。
     */
    fun resolve(ruleModuleId: String, config: MahjongRuleConfig? = null): RuleCatalogueResolution

    /** 凍結來源集合，不影響已註冊來源的查詢。 */
    fun freeze()
}
