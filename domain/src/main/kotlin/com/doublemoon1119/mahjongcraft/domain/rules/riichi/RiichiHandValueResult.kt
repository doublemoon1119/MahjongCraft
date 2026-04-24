package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult

/**
 * 立直麻將手牌價值計算結果。
 *
 * @property yakuResults 各役種的計算結果列表。
 * @property totalHan 總番數（役滿時為 -1，雙倍役滿時為 -2，以此類推）。
 * @property totalFu 總符數（滿貫以上時為 0）。
 * @property isYakuman 是否為役滿。
 */
data class RiichiHandValueResult(
    val yakuResults: List<YakuResult>,
    val totalHan: Int,
    val totalFu: Int,
    val isYakuman: Boolean = totalHan < 0
) : HandValueResult