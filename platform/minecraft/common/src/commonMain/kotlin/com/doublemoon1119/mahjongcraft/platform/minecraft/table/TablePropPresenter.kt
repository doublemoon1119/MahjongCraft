package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.uuid.Uuid

/**
 * 將規則描述的桌面物件同步到 Minecraft 世界的版本 adapter 邊界。
 *
 * 採差量同步：身分、內容、落點與朝向相符的物件原樣保留。完整場景通過驗證後才替換變動物件；
 * 無法完整呈現時清除失效顯示並回報，不留下部分成功結果。
 */
interface TablePropPresenter {
    /** 清除伺服器 session 的中央區域保留與暫存狀態；session 開始與結束時都會呼叫。 */
    fun resetSession() = Unit

    /**
     * 暫時保留本桌中央區域，避免開門擲骰期間重新擺放規則桌面物件。
     *
     * @param tableId 麻將桌 UUID。
     * @param generation 開局呈現世代；舊世代不能影響新世代。
     */
    fun reserveCenter(tableId: Uuid, generation: Long) = Unit

    /**
     * 釋放指定開局世代的中央區域保留。
     *
     * @param tableId 麻將桌 UUID。
     * @param generation 要釋放的開局呈現世代。
     * @return 確實釋放目前保留時為 `true`。
     */
    fun releaseCenter(tableId: Uuid, generation: Long): Boolean = false

    /**
     * 把指定桌子的規則桌面物件同步為 [presentation] 描述的完整清單。
     *
     * @param presentation 桌子目前應有的完整規則物件清單。
     * @return 同步處理結果。
     */
    fun present(presentation: TablePropPresentation): TablePropPresentationResult

    /**
     * 清除指定桌子目前所有的規則桌面物件。
     *
     * @param tableId 麻將桌 UUID。
     * @param tableLocation 麻將桌目前位置。
     * @return 實際移除的物件數量。
     */
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
 * @return 需要生成的目標與需要移除的既有物件。
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
