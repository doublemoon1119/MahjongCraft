package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule

/** 特定 [CompletedGameActionContext] 下，以純邏輯判定是否構成某種主動觸發的途中流局。 */
interface PostActionExhaustiveDrawResolver {
    /** Resolver 的識別碼，依註冊 ID 慣例使用 `namespace:path` 格式；同優先序時作為穩定排序的最後決勝鍵。 */
    val id: String

    /** 此 resolver 所屬的規則模組 ID。 */
    val ruleModuleId: String

    /** 數值越小越先判定；同優先序再依 [id] 排序。 */
    val priority: Int

    /**
     * 成立時回傳對應的流局原因，不認得 [context] 或不成立時回傳 `null`，讓下一個 resolver 繼續判定。
     *
     * @param context 已完成的遊戲動作情境。
     * @param ruleModule 目前對局使用的規則模組。
     * @return 成立時的流局原因；不成立或不適用時為 null。
     */
    fun resolve(context: CompletedGameActionContext, ruleModule: MahjongRuleModule<*>): ExhaustiveDrawReason?
}
