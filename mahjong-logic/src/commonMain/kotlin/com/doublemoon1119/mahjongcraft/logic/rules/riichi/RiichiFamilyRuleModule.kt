package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.module.DeclarationDiscardEffect
import com.doublemoon1119.mahjongcraft.logic.module.DeclarationDiscardPolicy
import com.doublemoon1119.mahjongcraft.logic.module.ExhaustiveDrawSettlementResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 日本麻將系列（四人與三人）規則模組共有、但不屬於 [MahjongRuleModule] 通用介面的判定。
 *
 * 流程層的流局滿貫與四槓散了判定透過這個介面呼叫，不需要分別認得每一種日本麻將規則模組。
 *
 * @param T 此模組使用的日本麻將規則配置型別。
 */
interface RiichiFamilyRuleModule<T : RiichiFamilyRuleConfig> : MahjongRuleModule<T> {
    /**
     * 判定流局滿貫並計算收支；沒有任何人成立時回傳 `null`。
     *
     * @param tableState 待判定的權威牌桌狀態。
     * @return 流局滿貫成立者與點數差額；無成立者時為 null。
     */
    fun resolveNagashiMangan(tableState: TableState): NagashiManganResolution?

    /**
     * 四槓散了成立時回傳對應的流局原因，否則回傳 `null`。
     *
     * @param tableState 待判定的權威牌桌狀態。
     * @return 四槓散了流局原因；條件不成立時為 null。
     */
    fun resolveSuukanNagare(tableState: TableState): ExhaustiveDrawReason?

    /**
     * 一般流局（牌山摸盡）結算，並讓 [notenDeclaredPlayerIds] 宣告不聽。
     *
     * 宣告不聽的玩家即使手牌聽牌，也不公開手牌、不算聽牌，照不聽支付罰符；其餘玩家與
     * [MahjongRuleModule.declareExhaustiveDraw] 的判定相同。已立直的玩家不能宣告不聽。
     *
     * @param tableState 待結算的權威牌桌狀態。
     * @param notenDeclaredPlayerIds 宣告不聽的玩家；必須都仍在局中且沒有立直。
     * @return 一般流局結算。
     * @throws IllegalArgumentException 宣告不聽的玩家不在局中或已立直時。
     */
    fun declareExhaustiveDraw(
        tableState: TableState,
        notenDeclaredPlayerIds: Set<Uuid>,
    ): ExhaustiveDrawSettlementResult

    /** 立直宣告與附帶捨牌的規則效果：扣一根立直棒，並記錄立直牌與一發資格。 */
    override fun declarationDiscardPolicy(): DeclarationDiscardPolicy = DeclarationDiscardPolicy { tableState, player, action, discardResult ->
        if (action != RIICHI_GAME_ACTION) return@DeclarationDiscardPolicy null
        val declaration = applyRiichiDeclaration(tableState, player, discardResult) ?: return@DeclarationDiscardPolicy null
        DeclarationDiscardEffect(
            player = declaration.player,
            dynamicRuleState = declaration.dynamicRuleState,
            cost = RIICHI_STICK_POINTS,
        )
    }
}
