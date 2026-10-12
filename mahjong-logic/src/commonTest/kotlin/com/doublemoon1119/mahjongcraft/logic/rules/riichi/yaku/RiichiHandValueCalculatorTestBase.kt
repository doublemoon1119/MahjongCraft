package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueCalculator

/**
 * 立直麻將手牌番數計算機測試基底類別。
 *
 * @see RiichiHandValueCalculator
 */
abstract class RiichiHandValueCalculatorTestBase {

    /** 共用的日麻手牌番數計算器。 */
    protected val calculator = RiichiHandValueCalculator()
}
