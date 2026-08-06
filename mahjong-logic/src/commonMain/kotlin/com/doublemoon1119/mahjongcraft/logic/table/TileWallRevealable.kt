package com.doublemoon1119.mahjongcraft.logic.table

import kotlin.uuid.Uuid

/**
 * 定義規則是否具有「揭露牌山中特定牌」的能力介面。
 *
 * 實作此介面的動態狀態類別（如 [com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState]）
 * 負責根據當前的對局數據，計算並提供應該在客戶端快照中公開顯示的牌實體唯一識別碼。
 */
interface TileWallRevealable {

    /**
     * 獲取目前對局狀態下應公開可見的牌唯一識別碼集合。
     *
     * 該方法會參考傳入的 [state] 資訊（如牌山剩餘張數、規則配置等），
     * 推算出例如「寶牌指示器」等應翻開牌張的 [Uuid]。
     *
     * @param state 當前的完整對局狀態，提供運算所需的上下文資訊。
     * @return 包含所有應公開 [com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile] 之 ID 的 [Set]
     */
    fun getVisibleTileIds(state: TableState): Set<Uuid>
}
