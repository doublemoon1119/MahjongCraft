package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.api.registration.WinCelebrationCueResolverRegistrar
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult

/** 規則模組 ID 與 [WinCelebrationCueResolver] 的註冊中心；擴充只透過 [WinCelebrationCueResolverRegistrar] 登記。 */
interface WinCelebrationCueResolverRegistry : WinCelebrationCueResolverRegistrar {

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
