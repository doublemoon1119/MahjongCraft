package com.doublemoon1119.mahjongcraft.flow.common.game.history

/**
 * 外層表示欄位已變更，與沒有變化的 null 外層區別。
 *
 * @param T 欄位值的型別，可包含 null。
 * @property value 欄位的新值；欄位本身可空時，此值也可以為 null。
 */
data class HistoryChangedValue<T>(
    val value: T,
)
