package com.doublemoon1119.mahjongcraft.logic.judgment

/**
 * 手牌價值計算機介面。
 *
 * 定義手牌價值（役種/台數）計算的通用方法，由各規則實作類（如立直麻將、台灣麻將）提供具體實現。
 *
 * @param C 上下文類型，必須實作 [HandValueContext]。
 * @param R 結果類型，必須實作 [HandValueResult]。
 */
interface HandValueCalculator<C : HandValueContext, R : HandValueResult> {

    /**
     * 計算手牌的價值（役種或台數）。
     *
     * 不同規則有不同的計算方式：
     * - 日本麻將：計算役種的翻數（Han）
     * - 台灣麻將：計算役種的台數（Dora）
     *
     * @param context 價值計算所需的上下文資訊。
     * @return 價值計算結果。
     */
    fun calculate(context: C): R
}
