package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 計算宣告動作與附帶捨牌所需規則變化的純 policy。
 *
 * Flow 會先以合法動作判定器確認宣告時機，再把結果交給這個 policy。policy 不得修改權威桌況、
 * 存取 repository 或發布副作用；它只回傳這次宣告允許套用的玩家與規則狀態變化。
 */
fun interface DeclarationDiscardPolicy {
    /**
     * 計算宣告及捨牌的規則效果。
     *
     * @param tableState 宣告前的完整權威桌況。
     * @param player 宣告玩家在宣告前的狀態。
     * @param action 要執行的擴充宣告動作。
     * @param discardResult 已依請求選牌完成的捨牌結果；包含未整理的捨牌後手牌。
     * @return 可由 Flow 驗證後套用的效果；此規則不支援該動作時為 null。
     */
    fun resolve(
        tableState: TableState,
        player: MahjongPlayer,
        action: GameAction.Extension,
        discardResult: Hand.DiscardResult,
    ): DeclarationDiscardEffect?
}
