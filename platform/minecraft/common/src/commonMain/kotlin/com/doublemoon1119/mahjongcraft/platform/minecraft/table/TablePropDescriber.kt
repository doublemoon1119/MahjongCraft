package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 描述一種規則此刻在桌上要擺哪些物件。
 *
 * 只描述、不生成：實際的 entity 由平台的通用執行器依描述建立、保留或移除。每次呼叫都回傳完整清單，
 * 沒列出的物件會被移除。
 */
fun interface TablePropDescriber {
    /**
     * 依目前的權威桌況描述桌上應有的物件。
     *
     * @param tableState 目前的權威桌況。
     * @return 桌上應有的全部物件；沒有任何物件時為空清單。
     */
    fun describe(tableState: TableState): List<TablePropPlacement>

    /**
     * 這個座位的副露角落被自己描述的物件佔用的寬度（單位：方塊），沿副露往手牌排列的方向量測。
     *
     * 副露從角落往手牌方向排開時會先讓開這段寬度，手牌也以此避開角落。預設不佔用。
     *
     * @param tableState 目前的權威桌況。
     * @param seatIndex 座位，即 `TableState.players` 的 index。
     */
    fun cornerWidth(tableState: TableState, seatIndex: Int): Double = 0.0
}
