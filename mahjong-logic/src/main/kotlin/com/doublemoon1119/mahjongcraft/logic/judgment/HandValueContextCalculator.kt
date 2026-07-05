package com.doublemoon1119.mahjongcraft.logic.judgment

/**
 * 手牌役種上下文計算機介面。
 *
 * 定義根據當前遊戲狀態計算役種結算所需上下文資訊的通用方法。
 * 由各規則實作類（如立直麻將、台灣麻將）提供具體實現。
 *
 * @param C 上下文類型，必須實作 [HandValueContext]。
 * @param I 計算所需的輸入參數型別。
 */
interface HandValueContextCalculator<C : HandValueContext, I> {

    /**
     * 根據當前遊戲狀態計算役種結算所需的上下文資訊。
     *
     * 不同規則有不同的上下文資訊：
     * - 日本麻將：寶牌指示牌、裏寶牌、海底撈月、河底撈魚、嶺上花等。
     * - 台灣麻將：台牌等。
     *
     * @param input 計算所需的輸入參數。
     * @return 計算後的上下文資訊。
     */
    fun calculate(input: I): C
}
