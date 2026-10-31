package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RuleCatalogueRegistrar

/** 供內建與第三方規則登記目錄來源的凍結式 registry；擴充只透過 [RuleCatalogueRegistrar] 登記。 */
interface RuleCatalogueRegistry : RuleCatalogueRegistrar {

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
