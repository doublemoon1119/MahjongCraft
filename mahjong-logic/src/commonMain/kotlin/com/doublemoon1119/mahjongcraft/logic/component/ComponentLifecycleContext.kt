package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.random.Random

/**
 * 正式開局或換局時建立組件的情境，讀取快照不使用此入口。
 *
 * @param O 對象型別。
 * @property ruleModuleId 目前規則識別碼。
 * @property tableState 新一局的權威候選桌況。
 * @property ownerType 組件對象定義。
 * @property owner 新一局的對象。
 * @property previousTableState 上一局已結算桌況；第一局為 null。
 * @property random 開局或換局建立組件時可用的亂數來源，需要隨機初值的組件只能使用它，不得改用系統亂數；
 *   對局進行中新建的捨牌紀錄、副露以及驗證時為 null。
 */
data class ComponentLifecycleContext<O : Any>(
    val ruleModuleId: String,
    val tableState: TableState,
    val ownerType: ComponentOwnerType<O>,
    val owner: O,
    val previousTableState: TableState?,
    val random: Random?,
) {
    init {
        ownerType.requireInstance(owner)
    }
}
