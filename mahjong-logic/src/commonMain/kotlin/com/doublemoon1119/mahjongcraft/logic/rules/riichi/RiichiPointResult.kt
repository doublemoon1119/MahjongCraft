package com.doublemoon1119.mahjongcraft.logic.rules.riichi

/**
 * 立直麻將的點數結算結果。
 *
 * 區分榮和（Ron）與自摸（Tsumo）兩種情境：
 * - 榮和由單一放銃者支付全額。
 * - 自摸由其餘玩家依身分分別支付，且每一筆支付會各自獨立進位到百位，
 *   其加總不必然等於「總點數乘以身分倍率後再一次性進位」的結果，
 *   因此無法僅用單一總點數表示，必須拆分為個別付款金額。
 */
sealed interface RiichiPointResult {

    /** 本次胡牌贏家實際獲得的點數總和。 */
    val total: Int

    /**
     * 榮和：由放銃者一人支付全額。
     *
     * @property total 放銃者支付的點數，亦即贏家獲得的總點數。
     */
    data class Ron(override val total: Int) : RiichiPointResult

    /**
     * 自摸，且贏家本身為莊家：三位閒家各支付相同點數。
     *
     * @property paymentPerNonDealer 每位閒家支付的點數。
     */
    data class DealerTsumo(val paymentPerNonDealer: Int) : RiichiPointResult {
        override val total: Int get() = paymentPerNonDealer * 3
    }

    /**
     * 自摸，且贏家為閒家：莊家與另外兩位閒家分別支付不同點數。
     *
     * @property dealerPayment 莊家支付的點數。
     * @property otherNonDealerPayment 另外兩位閒家各自支付的點數。
     */
    data class NonDealerTsumo(
        val dealerPayment: Int,
        val otherNonDealerPayment: Int,
    ) : RiichiPointResult {
        override val total: Int get() = dealerPayment + otherNonDealerPayment * 2
    }

    /**
     * 自摸包牌：因包牌責任成立（大三元／大四喜由碰／明槓完成），
     * 改由包牌責任者一人支付全額，取代原本應由三家或莊家/閒家分攤的自摸點數。
     *
     * 結果形狀與 [Ron] 相同（單一玩家支付全額），但實際付款人是包牌責任者而非放銃者，
     * 由呼叫端依 [com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoLiability.direction] 決定對象。
     *
     * @property total 包牌責任者支付的點數，亦即贏家獲得的總點數。
     */
    data class PaoTsumo(override val total: Int) : RiichiPointResult

    /**
     * 榮和包牌：因包牌責任成立，由包牌責任者與實際放銃者兩人平分點數。
     *
     * @property paymentEach 兩人各自支付的點數。
     */
    data class PaoRon(val paymentEach: Int) : RiichiPointResult {
        override val total: Int get() = paymentEach * 2
    }
}
