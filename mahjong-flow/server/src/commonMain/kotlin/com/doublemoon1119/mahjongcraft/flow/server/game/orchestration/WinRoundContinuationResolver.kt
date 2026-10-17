package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundContinuationContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule

/** 一次胡牌即時結算完成後，以規則特有邏輯判斷本局後續應採取的權威決策。 */
interface WinRoundContinuationResolver {
    /** Resolver 的識別碼，依註冊 ID 慣例使用 `namespace:path` 格式；同優先序時作為穩定排序的最後決勝鍵。 */
    val id: String

    /** 此 resolver 所屬的規則模組 ID。 */
    val ruleModuleId: String

    /** 數值越大越先判定；同優先序再依規則模組與 resolver 的完整登記身分排序。 */
    val priority: Int

    /**
     * 成立時回傳這次的權威決策，否則回傳 `null` 讓下一個 resolver 繼續判定。
     *
     * @param context 胡牌即時結算後的流程情境。
     * @param ruleModule 目前對局使用的規則模組。
     * @return 成立時的回合後續決策；不適用時為 null。
     */
    fun resolve(context: WinRoundContinuationContext, ruleModule: MahjongRuleModule<*>): WinRoundDirective?
}
