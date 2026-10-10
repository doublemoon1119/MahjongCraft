package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult

/** 規則模組 ID 與 [WinCelebrationCueResolver] 的註冊中心。 */
interface WinCelebrationCueResolverRegistry {
    /** 目前已登記規則模組 ID 的快照。 */
    val registrationKeys: Set<String>

    /** 註冊指定規則模組的 resolver；同一 ID 不得重複。 */
    fun register(ruleModuleId: String, resolver: WinCelebrationCueResolver)

    /** 凍結後不得再註冊。 */
    fun freeze()

    /** 解析指定規則結果；未註冊或不適用時回傳空清單。 */
    fun resolve(ruleModuleId: String, result: HandValueResult): List<String>
}
