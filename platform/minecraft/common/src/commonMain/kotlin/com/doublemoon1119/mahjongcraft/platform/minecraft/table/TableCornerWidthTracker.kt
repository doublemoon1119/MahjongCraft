package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.uuid.Uuid

/**
 * 每張桌子目前桌上物件佔用的副露角落寬度紀錄。
 *
 * 寬度跟著桌上實際擺放的物件走：物件只在特定時間點更新，權威桌況在兩次更新之間可能已經不同（例如和牌收走
 * 供託後，供託棒要到下一局才移除），擺手牌時讀這份紀錄，才能避開仍在桌上的物件。
 */
class TableCornerWidthTracker {
    /** 依桌子 UUID 索引的各座位寬度。 */
    private val widthsByTableId = mutableMapOf<Uuid, Map<Int, Double>>()

    /** 記錄指定桌子目前各座位的角落寬度，取代先前的紀錄。 */
    fun record(tableId: Uuid, widthsBySeat: Map<Int, Double>) {
        widthsByTableId[tableId] = widthsBySeat
    }

    /** 查詢指定桌子的紀錄；尚未記錄時回傳 null。 */
    fun find(tableId: Uuid): Map<Int, Double>? = widthsByTableId[tableId]

    /** 移除指定桌子的紀錄。 */
    fun clear(tableId: Uuid) {
        widthsByTableId.remove(tableId)
    }
}
