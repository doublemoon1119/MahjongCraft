package com.doublemoon1119.mahjongcraft.flow.api.registration

import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventPublicMarkerResolverDefinition

/** 擴充登記事件公開標記解析器時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface GameEventPublicMarkerResolverRegistrar {
    /** 目前已登記規則模組 ID 的快照。 */
    val registrationKeys: Set<String>

    /** 是否已禁止後續註冊。 */
    val isFrozen: Boolean

    /**
     * 登記指定規則模組的公開事件標記解析器。
     *
     * @param definition 規則模組識別碼與解析器。
     * @throws IllegalStateException registry 已凍結時拋出。
     * @throws IllegalArgumentException 識別碼無效或已登記時拋出。
     */
    fun register(definition: GameEventPublicMarkerResolverDefinition)
}
