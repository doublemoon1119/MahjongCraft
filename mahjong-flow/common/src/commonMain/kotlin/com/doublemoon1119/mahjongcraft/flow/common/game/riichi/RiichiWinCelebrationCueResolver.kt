package com.doublemoon1119.mahjongcraft.flow.common.game.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinCelebrationCueIds
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolver
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueResult

/** 列出這次和牌所有自然役滿的日麻解析器：役滿倍數高者在前，同倍數依役種定義順序。 */
object RiichiWinCelebrationCueResolver : WinCelebrationCueResolver {
    override fun resolve(result: HandValueResult): List<String> {
        val riichi = result as? RiichiHandValueResult ?: return emptyList()
        return riichi.yakuResults
            .filter { it.isYakuman }
            .sortedWith(compareBy({ it.han }, { it.yaku.ordinal }))
            .map { RiichiWinCelebrationCueIds.yakuman(it.yaku) }
    }
}
