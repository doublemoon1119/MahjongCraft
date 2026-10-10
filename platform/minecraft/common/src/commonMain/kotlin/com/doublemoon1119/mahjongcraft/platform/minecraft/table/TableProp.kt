package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 依描述算出每個座位副露角落被佔用的寬度，鍵為座位；沒有描述的規則每個座位都是 `0.0`。 */
fun TablePropDescriber?.cornerWidthsBySeat(tableState: TableState): Map<Int, Double> = tableState.players.indices.associateWith { seatIndex -> this?.cornerWidth(tableState, seatIndex) ?: 0.0 }
