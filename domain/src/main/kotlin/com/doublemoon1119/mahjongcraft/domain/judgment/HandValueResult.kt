package com.doublemoon1119.mahjongcraft.domain.judgment

/**
 * 手牌價值計算結果介面。
 *
 * 各規則實作類（如立直麻將、台灣麻將）可實作此介面以提供具體的計算結果類別。
 *
 * @see HandValueCalculator
 */
interface HandValueResult {
    /**
     * 總價值（翻數或台數）。
     *
     * 不同規則的定義：
     * - 日本麻將：番數（Han），役滿時為負值（-1 = 1倍役滿，-2 = 2倍役滿）
     * - 台灣麻將：台數（Dora），八大天王等特殊牌型可能為負值
     *
     * @return 總價值。
     */
    val totalValue: Int

    /**
     * 是否為特殊大牌型。
     *
     * 不同規則的定義：
     * - 日本麻將：是否為役滿
     * - 台灣麻將：是否為八大天王、倍八等特殊牌型
     *
     * @return 是否為特殊大牌型。
     */
    val isSpecial: Boolean
}