package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import kotlinx.serialization.Serializable

/**
 * 區分「欄位改為 null」與「欄位沒有變動」。
 *
 * @param T 欄位值的型別，可為可空型別。
 * @property value 欄位的新值；可空欄位的值也可以為 null。
 */
@Serializable
data class HistoryChangedValuePersistenceDto<T>(
    val value: T,
)
