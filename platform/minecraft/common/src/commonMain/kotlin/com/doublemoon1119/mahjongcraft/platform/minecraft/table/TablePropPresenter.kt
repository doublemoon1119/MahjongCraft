package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.uuid.Uuid

/**
 * 將規則描述的桌面物件同步到 Minecraft 世界的版本 adapter 邊界。
 *
 * 採差量同步：種類、變體、落點與朝向都相同的既有物件原樣保留，只替缺少的落點生成新物件，新物件全部生成
 * 成功後才移除多出來的舊物件。
 */
interface TablePropPresenter {
    /** 把指定桌子的規則桌面物件同步為 [presentation] 描述的完整清單。 */
    fun present(presentation: TablePropPresentation): TablePropPresentationResult

    /** 清除指定桌子目前所有的規則桌面物件；回傳實際移除數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}

/**
 * 比對目標清單與桌上既有物件。
 *
 * 每個既有物件最多對應一個目標；無法辨識身分（[targetOf] 回傳 null）的既有物件一律視為多餘。
 *
 * @param targets 桌上應有的全部物件落點。
 * @param existing 桌上目前由這張桌子管理的物件。
 * @param targetOf 讀出既有物件目前的身分與落點。
 */
fun <T> planTablePropSync(
    targets: List<TablePropTarget>,
    existing: List<T>,
    targetOf: (T) -> TablePropTarget?,
): TablePropSyncPlan<T> {
    val unmatched = existing.mapNotNull { entry -> targetOf(entry)?.let { entry to it } }.toMutableList()
    val unidentified = existing.filter { entry -> targetOf(entry) == null }
    val missing = targets.filter { target ->
        val index = unmatched.indexOfFirst { (_, current) -> target.matches(current) }
        if (index >= 0) {
            unmatched.removeAt(index)
            false
        } else {
            true
        }
    }
    return TablePropSyncPlan(
        missing = missing,
        surplus = unidentified + unmatched.map { (entry, _) -> entry },
    )
}
