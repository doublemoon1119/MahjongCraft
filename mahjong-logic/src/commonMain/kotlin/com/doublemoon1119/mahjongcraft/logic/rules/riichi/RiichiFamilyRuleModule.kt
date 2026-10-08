package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 日本麻將系列（四人與三人）規則模組共有、但不屬於 [MahjongRuleModule] 通用介面的判定。
 *
 * 流程層的流局滿貫與四槓散了判定透過這個介面呼叫，不需要分別認得每一種日本麻將規則模組。
 */
interface RiichiFamilyRuleModule<T : RiichiFamilyRuleConfig> : MahjongRuleModule<T> {
    /** 判定流局滿貫並計算收支；沒有任何人成立時回傳 `null`。 */
    fun resolveNagashiMangan(tableState: TableState): NagashiManganResolution?

    /** 四槓散了成立時回傳對應的流局原因，否則回傳 `null`。 */
    fun resolveSuukanNagare(tableState: TableState): ExhaustiveDrawReason?
}
