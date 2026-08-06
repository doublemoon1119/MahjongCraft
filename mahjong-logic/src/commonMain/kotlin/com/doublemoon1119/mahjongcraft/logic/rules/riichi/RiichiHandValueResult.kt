package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult

/**
 * 立直麻將手牌價值計算結果。
 *
 * @property yakuResults 各役種的計算結果列表。
 * @property totalHan 總番數（非役滿時為正整數，役滿時為 -1，雙倍役滿時為 -2，以此類推）。
 * @property totalFu 總符數（滿貫以上時為 0）。
 * @property pointResult 依榮和/自摸區分的點數結算結果，詳見 [RiichiPointResult]。
 * @property isYakuman 是否為役滿。
 * @property paoLiability 若 [pointResult] 為包牌情境（[RiichiPointResult.PaoTsumo]／[RiichiPointResult.PaoRon]），
 *                        此欄位記錄對應的包牌責任方位；否則為 null。呼叫端需自行將方位對應回實際玩家。
 */
data class RiichiHandValueResult(
    val yakuResults: List<YakuResult>,
    val totalHan: Int,
    val totalFu: Int,
    val pointResult: RiichiPointResult,
    val isYakuman: Boolean = totalHan < 0,
    val paoLiability: PaoLiability? = null,
) : HandValueResult {
    /** 贏家實際獲得的點數總和，等同於 [pointResult] 的 [RiichiPointResult.total]。 */
    val totalPoint: Int get() = pointResult.total
}
