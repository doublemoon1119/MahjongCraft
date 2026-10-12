package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/** 規則自訂說明目錄的來源；只組成說明資料，不執行權威規則判定。 */
interface RuleCatalogueProvider {
    /** 對應的 namespaced 規則模組識別碼。 */
    val ruleModuleId: String

    /**
     * 建立一般說明使用的預設配置；不表示房間或歷史實際配置。
     *
     * @return 供本來源建立一般說明目錄的規則配置。
     */
    fun defaultRuleConfig(): MahjongRuleConfig

    /**
     * 依配置建立目錄；不支援該配置型別時回傳 null，不以預設配置冒充成功。
     *
     * @param config 所選規則的實際配置或一般說明配置。
     * @return 說明目錄，或不支援配置時的 null。
     */
    fun catalogue(config: MahjongRuleConfig): RuleCatalogue?
}
