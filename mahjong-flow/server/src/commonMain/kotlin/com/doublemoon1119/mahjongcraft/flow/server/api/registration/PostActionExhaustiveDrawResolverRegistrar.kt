package com.doublemoon1119.mahjongcraft.flow.server.api.registration

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolver

/** 擴充登記動作後的流局解析器時取得的登記介面；解析與凍結由 MahjongCraft 進行。 */
interface PostActionExhaustiveDrawResolverRegistrar {
    /** 目前已登記 resolver ID 的快照。 */
    val registrationKeys: Set<String>

    /**
     * 登記 resolver；同一規則模組內 ID 不得重複，同一個 ID 可由不同規則模組各自登記。
     *
     * @param resolver 要登記的判定器。
     */
    fun register(resolver: PostActionExhaustiveDrawResolver)
}
