package com.doublemoon1119.mahjongcraft.flow.common.di

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInWinCelebrationCueIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationCue
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolver
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType

/** 內建日麻規則模組 ID。 */
val RIICHI_RULE_MODULE_ID: String = BuiltInRuleModuleIds.RIICHI

/** 註冊內建規則的胡牌展示提示解析器。 */
fun WinCelebrationCueResolverRegistry.registerBuiltInWinCelebrationCueResolvers() {
    register(RIICHI_RULE_MODULE_ID, RiichiWinCelebrationCueResolver)
}

/** 建立已註冊內建 resolver 且完成凍結的獨立 registry，供非 DI 測試使用。 */
fun createBuiltInWinCelebrationCueResolverRegistry(): WinCelebrationCueResolverRegistry = com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistryImpl().apply {
    registerBuiltInWinCelebrationCueResolvers()
    freeze()
}

/** 僅從自然役滿役種挑選穩定 primary cue 的日麻解析器。 */
private object RiichiWinCelebrationCueResolver : WinCelebrationCueResolver {
    override fun resolve(result: com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult): WinCelebrationCue? {
        val riichi = result as? RiichiHandValueResult ?: return null
        val primary = riichi.yakuResults
            .filter { it.isYakuman }
            .maxWithOrNull(compareBy({ -it.han }, { -priority.getValue(it.yaku) }))
            ?: return null
        return WinCelebrationCue(BuiltInWinCelebrationCueIds.riichiYakuman(primary.yaku.name.toSnakeCase()))
    }

    private val priority: Map<YakuType, Int> = listOf(
        YakuType.KokushiMusou13,
        YakuType.ChurenPoto9,
        YakuType.SuuankouTanki,
        YakuType.Daisuushii,
        YakuType.KokushiMusou,
        YakuType.ChurenPoto,
        YakuType.Tsuuiisou,
        YakuType.Ryuuuiisou,
        YakuType.Suuankou,
        YakuType.Sukantsu,
        YakuType.Shousuushi,
        YakuType.Daisangen,
        YakuType.Chinroutou,
        YakuType.Tenhou,
        YakuType.Chiihou,
    ).withIndex().associate { (index, yaku) -> yaku to index }

    private fun String.toSnakeCase(): String = replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()
}
