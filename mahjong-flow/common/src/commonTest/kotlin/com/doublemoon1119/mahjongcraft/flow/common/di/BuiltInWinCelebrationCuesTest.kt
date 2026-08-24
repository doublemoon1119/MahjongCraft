package com.doublemoon1119.mahjongcraft.flow.common.di

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPointResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 內建日麻役滿 showcase cue 的選擇規則測試。 */
class BuiltInWinCelebrationCuesTest {
    private val registry = createBuiltInWinCelebrationCueResolverRegistry()

    /** 自然役滿會解析成對應 cue。 */
    @Test
    fun resolvesNaturalYakuman() {
        val result = resultOf(YakuResult.yakuman(YakuType.KokushiMusou))

        assertEquals("mahjongcraft:kokushi_musou", registry.resolve(RIICHI_RULE_MODULE_ID, result)?.key)
    }

    /** 累計役滿沒有自然役滿役種，因此不產生 cue。 */
    @Test
    fun excludesKazoeYakuman() {
        val result = RiichiHandValueResult(
            yakuResults = listOf(YakuResult.han(YakuType.Riichi, 13)),
            totalHan = -1,
            totalFu = 0,
            pointResult = RiichiPointResult.Ron(32_000),
        )

        assertNull(registry.resolve(RIICHI_RULE_MODULE_ID, result))
    }

    /** 多個役滿先選倍數較高者。 */
    @Test
    fun prefersHigherYakumanMultiplier() {
        val result = resultOf(
            YakuResult.yakuman(YakuType.KokushiMusou),
            YakuResult.doubleYakuman(YakuType.SuuankouTanki),
        )

        assertEquals("mahjongcraft:suuankou_tanki", registry.resolve(RIICHI_RULE_MODULE_ID, result)?.key)
    }

    /** 倍數相同時使用固定優先序。 */
    @Test
    fun usesStablePriorityForEqualMultipliers() {
        val result = resultOf(
            YakuResult.yakuman(YakuType.Daisangen),
            YakuResult.yakuman(YakuType.Daisuushii),
        )

        assertEquals("mahjongcraft:daisuushii", registry.resolve(RIICHI_RULE_MODULE_ID, result)?.key)
    }

    private fun resultOf(vararg yakuResults: YakuResult): RiichiHandValueResult = RiichiHandValueResult(
        yakuResults = yakuResults.toList(),
        totalHan = yakuResults.sumOf(YakuResult::han),
        totalFu = 0,
        pointResult = RiichiPointResult.Ron(32_000),
    )
}
