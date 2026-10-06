package com.doublemoon1119.mahjongcraft.flow.common.di

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInWinCelebrationCueIds
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolver
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueResult

/** 內建日麻規則模組 ID。 */
val RIICHI_RULE_MODULE_ID: String = BuiltInRuleModuleIds.RIICHI

/** 列出這次和牌所有自然役滿的日麻解析器：役滿倍數高者在前，同倍數依役種定義順序。 */
object RiichiWinCelebrationCueResolver : WinCelebrationCueResolver {
    override fun resolve(result: HandValueResult): List<String> {
        val riichi = result as? RiichiHandValueResult ?: return emptyList()
        return riichi.yakuResults
            .filter { it.isYakuman }
            .sortedWith(compareBy({ it.han }, { it.yaku.ordinal }))
            .map { BuiltInWinCelebrationCueIds.riichiYakuman(it.yaku.name.toSnakeCase()) }
    }

    private fun String.toSnakeCase(): String = replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()
}
