package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 最後捨牌的所有反應結束後，以純邏輯判定一種特殊 round outcome。 */
interface PostReactionRoundOutcomeResolver {
    /** Resolver 的完整 namespaced ID，同時作為穩定排序的最後決勝鍵。 */
    val id: String

    /** 此 resolver 所屬的規則模組 ID。 */
    val ruleModuleId: String

    /** 數值越小越先判定；同優先序再依 [id] 排序。 */
    val priority: Int

    /**
     * 成立時回傳完整權威結果，否則回傳 `null` 讓下一個 resolver 繼續判定。
     *
     * @param tableState 反應視窗結束後的桌況。
     * @param ruleModule 目前對局使用的規則模組。
     * @return 成立時的特殊回合結果；不成立時為 null。
     */
    fun resolve(tableState: TableState, ruleModule: MahjongRuleModule<*>): ResolvedRoundOutcome?
}
