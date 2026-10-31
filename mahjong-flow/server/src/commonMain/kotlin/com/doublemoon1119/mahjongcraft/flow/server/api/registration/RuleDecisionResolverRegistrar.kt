package com.doublemoon1119.mahjongcraft.flow.server.api.registration

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolver

/** 擴充登記規則選擇解析器時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface RuleDecisionResolverRegistrar {
    /** 目前已登記規則模組 ID 的快照。 */
    val registrationKeys: Set<String>

    /**
     * 登記一個規則模組的解析器；ID 格式錯誤、重複或註冊表凍結時會失敗。
     *
     * @param resolver 要登記的選擇流程解析器。
     */
    fun register(resolver: RuleDecisionResolver)
}
