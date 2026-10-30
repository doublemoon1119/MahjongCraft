package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 產生授權組件輸出的情境；對象可省略，供已明確公開的純值使用。
 *
 * @param O 對象型別。
 * @property ruleModuleId 目前規則識別碼。
 * @property tableState 同一提交版本的權威桌況。
 * @property ownerType 對象定義。
 * @property owner 資料所屬對象；純展示值為 null，不借用任何局內身分。
 * @property reader 已由 Flow 驗證資格的讀取者。
 */
data class ComponentProjectionContext<O : Any>(
    val ruleModuleId: String,
    val tableState: TableState,
    val ownerType: ComponentOwnerType<O>,
    val owner: O?,
    val reader: ComponentReadContext,
) {
    init {
        owner?.let(ownerType::requireInstance)
    }
}
