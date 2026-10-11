package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 驗證規則模組公開的本局自動操作控制 ID。
 *
 * @param ids 規則模組支援的完整 namespaced control ID 集合。
 */
fun requireValidAutomaticControlIds(ids: Set<String>) {
    ids.forEach { id ->
        NamespacedId.requireValid(id) { "Automatic control ID must be namespaced: $id" }
    }
}
