package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationConfig
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** 開局準備解析器的可凍結註冊表。 */
class RoundPreparationResolverRegistry {
    /** 依規則模組 ID 索引的解析器。 */
    private val resolvers = mutableMapOf<String, RoundPreparationResolver>()

    /** 註冊時擷取的局前準備預設設定；凍結後不再重新讀取解析器。 */
    private val defaultConfigs = mutableMapOf<String, RoundPreparationConfig?>()

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
        defaultConfigs[resolver.ruleModuleId] = resolver.defaultRoundPreparationConfig
    }

    /**
     * 取得指定規則模組的解析器。
     *
     * @param ruleModuleId 規則模組識別碼。
     * @return 對應的解析器；未登記時為 null。
     */
    fun find(ruleModuleId: String): RoundPreparationResolver? = resolvers[ruleModuleId]

    /**
     * 取得指定規則註冊當下擷取的局前準備預設設定。
     *
     * @param ruleModuleId 規則模組識別碼。
     * @return 預設設定；未登記或不需要玩家準備時為 null。
     */
    fun findDefaultConfig(ruleModuleId: String): RoundPreparationConfig? = defaultConfigs[ruleModuleId]

    /** 凍結此註冊表。 */
    fun freeze() {
        frozen = true
    }
}
