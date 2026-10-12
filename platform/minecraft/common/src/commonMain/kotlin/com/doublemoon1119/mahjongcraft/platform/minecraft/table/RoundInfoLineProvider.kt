package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 依目前規則狀態建立桌面局況顯示行。 */
fun interface RoundInfoLineProvider {
    /**
     * 依目前權威桌況建立局況顯示行。
     *
     * @param tableState 目前的權威桌況。
     * @return 依顯示順序排列的局況行。
     */
    fun buildLines(tableState: TableState): List<RoundInfoLine>
}
