package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import kotlin.uuid.Uuid

/** 可由麻將桌生命週期統一立即清除的暫時性世界呈現實體。 */
interface TableOwnedPresentationEntity {
    /** 所屬麻將桌的穩定 UUID；資料尚未設定或已損壞時為 `null`。 */
    val managedTableId: Uuid?
}
