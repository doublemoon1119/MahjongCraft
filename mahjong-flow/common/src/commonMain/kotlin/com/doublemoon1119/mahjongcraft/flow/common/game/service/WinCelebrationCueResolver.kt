package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationWinner
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult

/** 將指定規則的手牌價值結果解析成值得額外展示的理由 ID；順序與用途見 [WinCelebrationWinner.cueIds]。 */
fun interface WinCelebrationCueResolver {
    /** 不需要額外展示時回傳空清單。 */
    fun resolve(result: HandValueResult): List<String>
}
