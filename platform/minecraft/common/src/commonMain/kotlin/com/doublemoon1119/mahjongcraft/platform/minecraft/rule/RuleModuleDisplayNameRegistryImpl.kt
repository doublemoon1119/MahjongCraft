package com.doublemoon1119.mahjongcraft.platform.minecraft.rule

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import org.koin.core.annotation.Single

/**
 * [RuleModuleDisplayNameRegistry] 的預設記憶體實作。
 *
 * 建構時不預先加入任何映射；內建與第三方顯示名稱使用相同的 [register] 流程，並由外層組裝完成後
 * 呼叫 [freeze]。
 */
@Single(binds = [RuleModuleDisplayNameRegistry::class])
class RuleModuleDisplayNameRegistryImpl : RuleModuleDisplayNameRegistry {
    /** 依規則模組 ID 保存顯示名稱 translation key。 */
    private val translationKeysByRuleModuleId = mutableMapOf<String, String>()

    override val registrationKeys: Set<String> get() = translationKeysByRuleModuleId.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: RuleModuleDisplayNameDefinition) {
        val ruleModuleId = definition.ruleModuleId
        val translationKey = definition.translationKey
        check(!isFrozen) { "Rule module display name registry is frozen" }
        NamespacedId.requireValid(ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        require(ruleModuleId !in translationKeysByRuleModuleId) {
            "Display name already registered for rule module: $ruleModuleId"
        }
        translationKeysByRuleModuleId[ruleModuleId] = translationKey
    }

    override fun freeze() {
        isFrozen = true
    }

    override fun find(ruleModuleId: String): String? = translationKeysByRuleModuleId[ruleModuleId]
}
