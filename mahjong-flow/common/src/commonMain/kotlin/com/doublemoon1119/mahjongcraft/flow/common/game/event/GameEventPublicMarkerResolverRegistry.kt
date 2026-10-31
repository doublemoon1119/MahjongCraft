package com.doublemoon1119.mahjongcraft.flow.common.game.event

import com.doublemoon1119.mahjongcraft.flow.api.registration.GameEventPublicMarkerResolverRegistrar
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** 以規則模組 ID 登記、bootstrap 後凍結的公開事件標記解析器；擴充只透過 [GameEventPublicMarkerResolverRegistrar] 登記。 */
class GameEventPublicMarkerResolverRegistry : GameEventPublicMarkerResolverRegistrar {
    /** 初始化時依規則 ID 登記的標記提供者。 */
    private val resolvers = linkedMapOf<String, GameEventPublicMarkerResolver>()

    /** 目前已登記規則模組 ID 的快照。 */
    override val registrationKeys: Set<String> get() = resolvers.keys.toSet()

    /** 是否已禁止後續註冊。 */
    override var isFrozen: Boolean = false
        private set

    /**
     * 登記指定規則模組的公開事件標記解析器。
     *
     * @param definition 規則模組識別碼與解析器。
     * @throws IllegalStateException registry 已凍結時拋出。
     * @throws IllegalArgumentException 識別碼無效或已登記時拋出。
     */
    override fun register(definition: GameEventPublicMarkerResolverDefinition) {
        check(!isFrozen) { "Game event public marker resolver registry is frozen" }
        NamespacedId.requireValid(definition.ruleModuleId) {
            "Rule module ID must be namespaced: ${definition.ruleModuleId}"
        }
        require(resolvers.putIfAbsent(definition.ruleModuleId, definition.resolver) == null) {
            "Duplicate game event public marker resolver: ${definition.ruleModuleId}"
        }
    }

    /**
     * 查詢指定規則模組的 resolver；未登記時回傳 null。
     *
     * @param ruleModuleId 要查詢的完整規則模組識別碼。
     * @return 對應解析器，或未登記時為 null。
     */
    fun find(ruleModuleId: String): GameEventPublicMarkerResolver? = resolvers[ruleModuleId]

    /** 凍結 registry，禁止後續註冊。 */
    fun freeze() {
        isFrozen = true
    }
}
