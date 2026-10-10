package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 將權威胡牌結果轉換為規則專屬的語意詳情欄位，不指定其呈現方式。 */
interface WinSettlementDetailResolver {
    /**
     * 解析一般胡牌（自摸／榮和）的詳情；[handValue] 一律是實際成立的胡牌結果。
     *
     * @param state 胡牌成立時的權威桌況。
     * @param handValue 已完成計算的胡牌結果。
     * @return 規則專屬的胡牌詳情欄位。
     */
    fun resolve(state: TableState, handValue: HandValueResult): List<WinSettlementDetailField>

    /**
     * 解析不含 [HandValueResult] 的特殊胡牌等價結果，例如流局滿貫。
     *
     * @param state 特殊結果成立時的權威桌況。
     * @param outcome 欲解析的特殊回合結果。
     * @return 規則專屬詳情欄位；不認得結果時為 null。
     */
    fun resolveSpecialOutcome(state: TableState, outcome: ResolvedRoundOutcome): List<WinSettlementDetailField>? = null
}
