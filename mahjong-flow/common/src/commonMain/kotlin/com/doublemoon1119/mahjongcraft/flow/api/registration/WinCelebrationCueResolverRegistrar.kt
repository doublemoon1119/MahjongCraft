package com.doublemoon1119.mahjongcraft.flow.api.registration

import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverDefinition

/** 擴充登記和牌演出提示解析器時取得的登記介面；解析與凍結由 MahjongCraft 進行。 */
interface WinCelebrationCueResolverRegistrar {
    /** 目前已登記規則模組 ID 的快照。 */
    val registrationKeys: Set<String>

    /**
     * 註冊指定規則模組的 resolver；規則模組 ID 必須符合命名空間格式且不得重複，凍結後不可註冊。
     *
     * @param definition 完整的規則模組 ID 與 resolver 定義。
     */
    fun register(definition: WinCelebrationCueResolverDefinition)
}
