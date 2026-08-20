package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import kotlin.uuid.Uuid

/**
 * 定義牌河是否具有「側身標示某張牌」的能力介面。
 *
 * 實作此介面的 [DiscardPile] 類別（如 [RiichiDiscardPile]）負責根據自身紀錄，計算並
 * 提供目前應該側身呈現的那張牌唯一識別碼——例如立直宣告牌。不具備這種概念的規則（如台灣麻將）不需要
 * 實作此介面，呼叫端以 `discardPile as? SidewaysMarkedDiscardPile` 取用，讓平台呈現層與泛用用例
 * 都不需要認識「立直」這個規則專屬概念。
 */
interface SidewaysMarkedDiscardPile {
    /**
     * 獲取目前應該側身標示的那張牌唯一識別碼。
     *
     * @return 應側身呈現的 [IdentifiedTile] 唯一識別碼；
     *         沒有任何一張需要側身時回傳 `null`。
     */
    fun sidewaysMarkedTileId(): Uuid?
}
