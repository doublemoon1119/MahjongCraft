package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 依目前規則狀態建立桌面局況顯示行。 */
fun interface RoundInfoLineProvider {
    fun buildLines(tableState: TableState): List<RoundInfoLine>
}
