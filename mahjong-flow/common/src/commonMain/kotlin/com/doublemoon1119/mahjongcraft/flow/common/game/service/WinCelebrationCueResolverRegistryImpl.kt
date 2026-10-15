package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult

/** [WinCelebrationCueResolverRegistry] 的記憶體實作。 */
class WinCelebrationCueResolverRegistryImpl : WinCelebrationCueResolverRegistry {
    /** 依規則模組 ID 登記的胡牌展示理由 resolver。 */
    private val resolvers = mutableMapOf<String, WinCelebrationCueResolver>()

    /** registry 是否已凍結並禁止新增 resolver。 */
    private var frozen = false

    override val registrationKeys: Set<String> get() = resolvers.keys.toSet()

    override fun register(definition: WinCelebrationCueResolverDefinition) {
        check(!frozen) { "Win celebration cue resolver registry is frozen" }
        NamespacedId.requireValid(definition.ruleModuleId) { "Rule module ID must be namespaced: ${definition.ruleModuleId}" }
        require(resolvers.putIfAbsent(definition.ruleModuleId, definition.resolver) == null) { "Duplicate win celebration cue resolver: ${definition.ruleModuleId}" }
    }

    override fun freeze() {
        frozen = true
    }

    override fun resolve(ruleModuleId: String, result: HandValueResult): List<String> = resolvers[ruleModuleId]?.resolve(result).orEmpty()
}
