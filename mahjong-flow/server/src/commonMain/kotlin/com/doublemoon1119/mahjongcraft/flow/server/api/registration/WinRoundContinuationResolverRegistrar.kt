package com.doublemoon1119.mahjongcraft.flow.server.api.registration

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolver

/** 擴充登記和牌後續局方式解析器時取得的登記介面；解析與凍結由 MahjongCraft 進行。 */
interface WinRoundContinuationResolverRegistrar {
    /** 目前已登記 resolver 的完整身分快照，格式為 `ruleModuleId/resolverId`。 */
    val registrationKeys: Set<String>

    /**
     * 登記 resolver；同一規則模組內的 resolver ID 不得重複，不同規則模組可使用相同 ID。
     * 註冊表凍結後不可登記。
     *
     * @param resolver 要登記的胡牌後續判定器。
     */
    fun register(resolver: WinRoundContinuationResolver)
}
