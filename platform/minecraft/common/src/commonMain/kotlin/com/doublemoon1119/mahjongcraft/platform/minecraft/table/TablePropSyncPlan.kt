package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * 差量同步的結果。
 *
 * @property missing 桌上還沒有、需要生成的落點。
 * @property surplus 不在目標清單中、需要移除的既有物件。
 */
data class TablePropSyncPlan<T>(
    val missing: List<TablePropTarget>,
    val surplus: List<T>,
)
