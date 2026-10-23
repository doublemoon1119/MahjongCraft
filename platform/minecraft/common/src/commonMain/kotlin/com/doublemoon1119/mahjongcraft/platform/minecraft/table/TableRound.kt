package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 一場對局中的某一局；連莊時本場數不同，視為不同的一局。
 *
 * @property roundNumber 局數。
 * @property comboCount 連莊次數。
 */
data class TableRound(val roundNumber: Int, val comboCount: Int) {
    companion object {
        /**
         * 取得桌況目前所在的那一局。
         *
         * @param tableState 權威桌況。
         * @return 桌況目前所在的那一局。
         */
        fun of(tableState: TableState): TableRound = TableRound(tableState.roundNumber, tableState.comboCount)
    }
}
