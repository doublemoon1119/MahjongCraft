package com.doublemoon1119.mahjongcraft.flow.server.api.registration

import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverDefinition

/** 擴充登記和牌結算明細解析器時取得的登記介面；解析與凍結由 MahjongCraft 進行。 */
interface WinSettlementDetailResolverRegistrar {
    /** 目前已登記規則模組 ID 的快照。 */
    val registrationKeys: Set<String>

    /** 是否已禁止後續註冊。 */
    val isFrozen: Boolean

    /**
     * 登記指定規則模組的胡牌詳情解析器。
     *
     * @param definition 規則模組識別碼與胡牌詳情解析器的完整註冊定義。
     * @throws IllegalStateException registry 已凍結時拋出。
     * @throws IllegalArgumentException 識別碼格式無效或識別碼已被登記時拋出。
     */
    fun register(definition: WinSettlementDetailResolverDefinition)
}
