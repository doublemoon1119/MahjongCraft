package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 每張桌子目前桌上物件佔用的副露角落寬度紀錄。
 *
 * 寬度跟著桌上實際擺放的物件走：物件只在特定時間點更新，權威桌況在兩次更新之間可能已經不同（例如和牌收走
 * 供託後，供託棒要到下一局才移除），擺手牌時讀這份紀錄，才能避開仍在桌上的物件。紀錄附帶它所屬的那一局，
 * 只有同一局的紀錄才能拿來比較寬度是否改變。
 */
class TableCornerWidthTracker {
    /**
     * 一張桌子的紀錄。
     *
     * @property round 紀錄所屬的那一局。
     * @property widthsBySeat 依固定座位 index 索引的角落寬度。
     */
    private data class Entry(val round: TableRound, val widthsBySeat: Map<Int, Double>)

    /** 依桌子 UUID 索引的紀錄。 */
    private val entriesByTableId = mutableMapOf<Uuid, Entry>()

    /**
     * 記錄指定桌子目前各座位的角落寬度，取代先前的紀錄。
     *
     * @param tableId 麻將桌 UUID。
     * @param round 這份寬度所屬的那一局。
     * @param widthsBySeat 依固定座位 index 索引的角落寬度。
     */
    fun record(tableId: Uuid, round: TableRound, widthsBySeat: Map<Int, Double>) {
        entriesByTableId[tableId] = Entry(round, widthsBySeat)
    }

    /**
     * 只更新一個座位的寬度；紀錄屬於另一局時，捨棄舊紀錄，只留下這個座位。
     *
     * @param tableId 麻將桌 UUID。
     * @param round 這份寬度所屬的那一局。
     * @param seatIndex 固定座位 index。
     * @param width 這個座位的角落寬度。
     */
    fun recordSeat(tableId: Uuid, round: TableRound, seatIndex: Int, width: Double) {
        val current = entriesByTableId[tableId]?.takeIf { it.round == round }?.widthsBySeat.orEmpty()
        entriesByTableId[tableId] = Entry(round, current + (seatIndex to width))
    }

    /**
     * 查詢指定桌子的紀錄；尚未記錄時回傳 null。
     *
     * @param tableId 麻將桌 UUID。
     * @return 各座位角落寬度；尚未記錄時為 `null`。
     */
    fun find(tableId: Uuid): Map<Int, Double>? = entriesByTableId[tableId]?.widthsBySeat

    /**
     * 查詢指定桌子在 [round] 這一局的紀錄。
     *
     * @param tableId 麻將桌 UUID。
     * @param round 要查詢的那一局。
     * @return 各座位角落寬度；尚未記錄或紀錄屬於另一局時為 `null`。
     */
    fun find(tableId: Uuid, round: TableRound): Map<Int, Double>? = entriesByTableId[tableId]?.takeIf { it.round == round }?.widthsBySeat

    /**
     * 移除指定桌子的紀錄。
     *
     * @param tableId 麻將桌 UUID。
     */
    fun clear(tableId: Uuid) {
        entriesByTableId.remove(tableId)
    }
}

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
