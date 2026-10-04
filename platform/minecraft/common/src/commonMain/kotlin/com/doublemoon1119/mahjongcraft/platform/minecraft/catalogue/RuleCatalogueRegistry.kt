package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/** 規則自訂說明目錄的來源；只組成說明資料，不執行權威規則判定。 */
interface RuleCatalogueProvider {
    /** 對應的 namespaced 規則模組識別碼。 */
    val ruleModuleId: String

    /** 建立一般說明使用的預設配置；不表示房間或歷史實際配置。 */
    fun defaultRuleConfig(): MahjongRuleConfig

    /**
     * 依配置建立目錄；不支援該配置型別時回傳 null，不以預設配置冒充成功。
     *
     * @param config 所選規則的實際配置或一般說明配置。
     * @return 說明目錄，或不支援配置時的 null。
     */
    fun catalogue(config: MahjongRuleConfig): RuleCatalogue?
}

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
     * @return 可用目錄或明確的缺失原因。
     */
    fun resolve(ruleModuleId: String, config: MahjongRuleConfig? = null): RuleCatalogueResolution

    /** 凍結來源集合，不影響已註冊來源的查詢。 */
    fun freeze()
}

/** [RuleCatalogueRegistry] 的記憶體實作；registry 不捕捉來源程式錯誤以冒充空目錄。 */
class RuleCatalogueRegistryImpl : RuleCatalogueRegistry {
    /** 依註冊順序保存來源。 */
    private val providers = linkedMapOf<String, RuleCatalogueProvider>()

    /** 註冊規則的獨立快照。 */
    override val registrationKeys: Set<String> get() = providers.keys.toSet()

    /** 是否已完成註冊。 */
    override var isFrozen: Boolean = false
        private set

    /** 驗證來源識別碼並登記，重複註冊不覆蓋原來源。 */
    override fun register(provider: RuleCatalogueProvider) {
        check(!isFrozen) { "Rule catalogue registry is frozen" }
        NamespacedId.requireValid(provider.ruleModuleId) { "Invalid catalogue rule ID: ${provider.ruleModuleId}" }
        require(provider.ruleModuleId !in providers) { "Duplicate rule catalogue: ${provider.ruleModuleId}" }
        providers[provider.ruleModuleId] = provider
    }

    /** 只向指定規則來源解析，保留實際配置與一般說明的區別。 */
    override fun resolve(ruleModuleId: String, config: MahjongRuleConfig?): RuleCatalogueResolution {
        val provider = providers[ruleModuleId] ?: return RuleCatalogueResolution.MissingProvider
        val catalogue = provider.catalogue(config ?: provider.defaultRuleConfig()) ?: return RuleCatalogueResolution.UnsupportedConfig
        return RuleCatalogueResolution.Available(catalogue, usesDefaultConfig = config == null)
    }

    /** 結束註冊階段。 */
    override fun freeze() {
        isFrozen = true
    }
}
