package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 計算同一對象衍生效果的權威輸入，不包含觀察者身分。
 *
 * @param O 對象型別。
 * @property ruleModuleId 目前規則的完整識別碼。
 * @property tableState 同一版本的權威桌況。
 * @property ownerType 對象定義。
 * @property owner 這次計算的對象。
 */
data class ComponentEffectContext<O : Any>(
    val ruleModuleId: String,
    val tableState: TableState,
    val ownerType: ComponentOwnerType<O>,
    val owner: O,
) {
    init {
        ownerType.requireInstance(owner)
    }
}
