package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 驗證讀取或寫入的完整組件，不修改候選桌況或產生副作用。 */
fun interface AuthoritativeComponentValidator {
    /**
     * 檢查正式定義、適用對象及必要值；必要值、定義或對象不符時拒絕使用。
     *
     * @param state 已解碼或即將寫入的權威桌況。
     */
    fun validate(state: TableState)
}
