package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

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

    /** 只向指定規則來源解析，保留實際配置與一般說明的區別；一般說明清除條目的不適用原因。 */
    override fun resolve(ruleModuleId: String, config: MahjongRuleConfig?): RuleCatalogueResolution {
        val provider = providers[ruleModuleId] ?: return RuleCatalogueResolution.MissingProvider
        if (config != null) {
            val catalogue = provider.catalogue(config) ?: return RuleCatalogueResolution.UnsupportedConfig
            return RuleCatalogueResolution.Available(catalogue, usesDefaultConfig = false)
        }
        val catalogue = provider.catalogue(provider.defaultRuleConfig()) ?: return RuleCatalogueResolution.UnsupportedConfig
        val general = catalogue.copy(entries = catalogue.entries.map { it.copy(unavailableReasonTranslationKey = null) })
        return RuleCatalogueResolution.Available(general, usesDefaultConfig = true)
    }

    /** 結束註冊階段。 */
    override fun freeze() {
        isFrozen = true
    }
}
