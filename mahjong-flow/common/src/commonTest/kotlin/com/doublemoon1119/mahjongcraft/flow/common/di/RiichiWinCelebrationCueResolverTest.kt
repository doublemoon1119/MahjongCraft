package com.doublemoon1119.mahjongcraft.flow.common.di

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPointResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledWinCelebrationCueResolverRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 內建日麻胡牌展示理由的輸出規則測試。 */
class RiichiWinCelebrationCueResolverTest {
    private val registry = bundledWinCelebrationCueResolverRegistry()

    /** 自然役滿會解析成對應的展示理由。 */
    @Test
    fun resolvesNaturalYakuman() {
        val result = resultOf(YakuResult.yakuman(YakuType.KokushiMusou))

        assertEquals(listOf("mahjongcraft:riichi/yakuman/kokushi_musou"), registry.resolve(RIICHI_RULE_MODULE_ID, result))
    }

    /** 累計役滿沒有自然役滿役種，因此沒有展示理由。 */
    @Test
    fun excludesKazoeYakuman() {
        val result = RiichiHandValueResult(
            yakuResults = listOf(YakuResult.han(YakuType.Riichi, 13)),
            totalHan = -1,
            totalFu = 0,
            pointResult = RiichiPointResult.Ron(32_000),
        )

        assertTrue(registry.resolve(RIICHI_RULE_MODULE_ID, result).isEmpty())
    }

    /** 多個役滿全部列出：倍數高者在前，同倍數依役種定義順序。 */
    @Test
    fun listsAllYakumanByMultiplierThenDefinitionOrder() {
        val result = resultOf(
            YakuResult.yakuman(YakuType.Daisuushii),
            YakuResult.yakuman(YakuType.Daisangen),
            YakuResult.doubleYakuman(YakuType.SuuankouTanki),
        )

        assertEquals(
            listOf("mahjongcraft:riichi/yakuman/suuankou_tanki", "mahjongcraft:riichi/yakuman/daisangen", "mahjongcraft:riichi/yakuman/daisuushii"),
            registry.resolve(RIICHI_RULE_MODULE_ID, result),
        )
    }

    /** 古役役滿和一般役滿一樣列出展示理由。 */
    @Test
    fun listsLocalYakuman() {
        val result = resultOf(YakuResult.yakuman(YakuType.Renhou), YakuResult.doubleYakuman(YakuType.Daichisei), YakuResult.yakuman(YakuType.IshinoUeSannen))

        assertEquals(
            listOf("mahjongcraft:riichi/yakuman/daichisei", "mahjongcraft:riichi/yakuman/renhou", "mahjongcraft:riichi/yakuman/ishino_ue_sannen"),
            registry.resolve(RIICHI_RULE_MODULE_ID, result),
        )
    }

    /** 役種名稱中的數字前面也加底線。 */
    @Test
    fun separatesDigitsInYakuNames() {
        val result = resultOf(YakuResult.doubleYakuman(YakuType.KokushiMusou13), YakuResult.doubleYakuman(YakuType.ChurenPoto9))

        assertEquals(
            listOf("mahjongcraft:riichi/yakuman/kokushi_musou_13", "mahjongcraft:riichi/yakuman/churen_poto_9"),
            registry.resolve(RIICHI_RULE_MODULE_ID, result),
        )
    }

    /** 沒有登記解析器的規則沒有展示理由。 */
    @Test
    fun unregisteredRuleHasNoCues() {
        assertTrue(registry.resolve("custom:rule", resultOf(YakuResult.yakuman(YakuType.KokushiMusou))).isEmpty())
    }

    private fun resultOf(vararg yakuResults: YakuResult): RiichiHandValueResult = RiichiHandValueResult(
        yakuResults = yakuResults.toList(),
        totalHan = yakuResults.sumOf(YakuResult::han),
        totalFu = 0,
        pointResult = RiichiPointResult.Ron(32_000),
    )
}
