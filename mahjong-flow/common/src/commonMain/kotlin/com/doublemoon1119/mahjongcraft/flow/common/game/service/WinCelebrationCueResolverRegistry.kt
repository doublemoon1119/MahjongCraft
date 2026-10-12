package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult

/** 規則模組 ID 與 [WinCelebrationCueResolver] 的註冊中心。 */
interface WinCelebrationCueResolverRegistry {
    /** 目前已登記規則模組 ID 的快照。 */
    val registrationKeys: Set<String>

    /**
     * 註冊指定規則模組的 resolver；ID 不可空白或重複，凍結後不可註冊。
     *
     * @param ruleModuleId 要註冊 resolver 的規則模組 ID。
     * @param resolver 負責解析該規則手牌結果的 resolver。
     */
    fun register(ruleModuleId: String, resolver: WinCelebrationCueResolver)

    /** 凍結後不得再註冊。 */
    fun freeze()

    /**
     * 解析指定規則結果；未註冊或不適用時回傳空清單。
     *
     * @param ruleModuleId 要查找的規則模組 ID。
     * @param result 要交由對應 resolver 解析的手牌價值結果。
     * @return 值得額外展示的理由 ID；未註冊或不適用時為空清單。
     */
    fun resolve(ruleModuleId: String, result: HandValueResult): List<String>
}
