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
}
