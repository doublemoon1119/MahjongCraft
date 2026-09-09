package com.doublemoon1119.mahjongcraft.logic.base

/**
 * 和局原因的基礎介面。
 *
 * 用於定義遊戲中導致和局（流局）的各種類型，
 * 並允許各規則模組擴展定義自己的和局類型。
 */
interface ExhaustiveDrawReason {
    /** 供序列化、顯示註冊與第三方整合使用的完整 namespaced ID。 */
    val id: String

    /**
     * 選擇宣告這個流局理由的動作後，操作 HUD 卡片要預覽哪些立牌；預設不特別預覽（回傳空清單）。
     */
    fun previewTiles(hand: Hand): List<Tile> = emptyList()
}
