package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationConfig
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/** 開局準備解析器的可凍結註冊表。 */
class RoundPreparationResolverRegistry {
    /** 依規則模組 ID 索引的解析器。 */
    private val resolvers = mutableMapOf<String, RoundPreparationResolver>()

    /** 是否已禁止後續註冊。 */
    private var frozen = false

    /** 目前已登記規則模組 ID 的快照。 */
    val registrationKeys: Set<String> get() = resolvers.keys.toSet()

    /**
     * 登記一個規則模組的解析器；ID 格式錯誤、重複或註冊表凍結時會失敗。
     *
     * @param resolver 要登記的準備流程解析器。
     */
    fun register(resolver: RoundPreparationResolver) {
        check(!frozen) { "Round preparation resolver registry is frozen" }
        NamespacedId.requireValid(resolver.ruleModuleId) {
            "Round preparation rule module ID must be namespaced: ${resolver.ruleModuleId}"
        }
        require(resolver.ruleModuleId !in resolvers) {
            "Round preparation resolver already registered for ${resolver.ruleModuleId}"
        }
        resolvers[resolver.ruleModuleId] = resolver
    }

    /**
     * 取得指定規則模組的解析器。
     *
     * @param ruleModuleId 規則模組識別碼。
     * @return 對應的解析器；未登記時為 null。
     */
    fun find(ruleModuleId: String): RoundPreparationResolver? = resolvers[ruleModuleId]

    /**
     * 依完整規則設定取得局前準備的預設設定。
     *
     * @param ruleModuleId 規則模組識別碼。
     * @param ruleConfig 目前對局使用的規則設定。
     * @return 需要準備時間時的預設設定；不需要時為 null。
     */
    fun findDefaultConfig(ruleModuleId: String, ruleConfig: MahjongRuleConfig): RoundPreparationConfig? = resolvers[ruleModuleId]?.roundPreparationConfig(ruleConfig)

    /** 凍結此註冊表。 */
    fun freeze() {
        frozen = true
    }
}
