package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.logic.judgment.StandardHandDecomposition
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiResult

/**
 * 台灣麻將的手牌計台結果，不包含依付款人而異的莊家台。
 *
 * @property isWinningHand 是否符合牌型與最低台數。
 * @property taiResults 選定拆法中成立的項目。
 * @property totalTai 手牌總台數；有效的零台和牌仍可為0。
 * @property decomposition 產生此結果的標準型拆法；無效牌型為null。
 */
data class TaiwanHandValueResult(
    val isWinningHand: Boolean,
    val taiResults: List<TaiwanTaiResult>,
    val totalTai: Int,
    val decomposition: StandardHandDecomposition?,
) : HandValueResult
