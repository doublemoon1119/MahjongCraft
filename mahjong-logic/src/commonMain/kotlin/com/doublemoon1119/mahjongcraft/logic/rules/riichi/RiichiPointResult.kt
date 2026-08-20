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
     * 改由包牌責任者一人支付包牌部分的點數，取代原本應由三家分攤的自摸點數。
     *
     * 結果形狀與 [Ron] 相同（單一玩家支付全額），但實際付款人是包牌責任者而非放銃者，
     * 由呼叫端依 [PaoLiability.direction] 決定對象。
     *
     * @property paoPayment 包牌責任者支付的點數——依觸發包牌的那個役滿本身的倍數換算（大三元 1
     *           倍、大四喜 2 倍，見 [com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult.doubleYakuman]），不是整體役滿倍數。
     * @property remainder 若和了同時疊加其他役滿（例如大四喜 + 四暗刻），超出包牌範圍的那部分改走
     *           正常自摸結算（[DealerTsumo]/[NonDealerTsumo]），由呼叫端疊加進正常付款對象；沒有
     *           疊加時為 null，行為與過去完全相同。
     */
    data class PaoTsumo(val paoPayment: Int, val remainder: RiichiPointResult? = null) : RiichiPointResult {
        override val total: Int get() = paoPayment + (remainder?.total ?: 0)
    }

    /**
     * 榮和包牌：因包牌責任成立，由包牌責任者與實際放銃者兩人平分包牌部分的點數。
     *
     * @property paymentEach 兩人各自支付的點數（依觸發包牌的役滿本身倍數換算，理由同
     *           [PaoTsumo.paoPayment]）。
     * @property remainder 若和了同時疊加其他役滿，超出包牌範圍的那部分改走正常榮和結算（[Ron]），
     *           由放銃者全額支付、疊加進正常付款對象；沒有疊加時為 null，行為與過去完全相同。
     */
    data class PaoRon(val paymentEach: Int, val remainder: RiichiPointResult? = null) : RiichiPointResult {
        override val total: Int get() = paymentEach * 2 + (remainder?.total ?: 0)
    }
}
