package com.doublemoon1119.mahjongcraft.flow.common.game.riichi

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
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
    fun `resolves natural yakuman`() {
        val result = resultOf(YakuResult.yakuman(YakuType.KokushiMusou))

        assertEquals(listOf("mahjongcraft:riichi/yakuman/kokushi_musou"), registry.resolve(BuiltInRuleModuleIds.RIICHI, result))
    }

    /** 累計役滿沒有自然役滿役種，因此沒有展示理由。 */
    @Test
    fun `excludes kazoe yakuman`() {
        val result = RiichiHandValueResult(
            yakuResults = listOf(YakuResult.han(YakuType.Riichi, 13)),
            totalHan = -1,
            totalFu = 0,
            pointResult = RiichiPointResult.Ron(32_000),
        )

        assertTrue(registry.resolve(BuiltInRuleModuleIds.RIICHI, result).isEmpty())
    }

    /** 多個役滿全部列出：倍數高者在前，同倍數依役種定義順序。 */
    @Test
    fun `lists all yakuman by multiplier then definition order`() {
        val result = resultOf(
            YakuResult.yakuman(YakuType.Daisuushii),
            YakuResult.yakuman(YakuType.Daisangen),
            YakuResult.doubleYakuman(YakuType.SuuankouTanki),
        )

        assertEquals(
            listOf("mahjongcraft:riichi/yakuman/suuankou_tanki", "mahjongcraft:riichi/yakuman/daisangen", "mahjongcraft:riichi/yakuman/daisuushii"),
            registry.resolve(BuiltInRuleModuleIds.RIICHI, result),
        )
    }

    /** 古役役滿和一般役滿一樣列出展示理由。 */
    @Test
    fun `lists local yakuman`() {
        val result = resultOf(YakuResult.yakuman(YakuType.Renhou), YakuResult.doubleYakuman(YakuType.Daichisei), YakuResult.yakuman(YakuType.IshinoUeSannen))

        assertEquals(
            listOf("mahjongcraft:riichi/yakuman/daichisei", "mahjongcraft:riichi/yakuman/renhou", "mahjongcraft:riichi/yakuman/ishino_ue_sannen"),
            registry.resolve(BuiltInRuleModuleIds.RIICHI, result),
        )
    }

    /** 役種名稱中的數字前面也加底線。 */
    @Test
    fun `separates digits in yaku names`() {
        val result = resultOf(YakuResult.doubleYakuman(YakuType.KokushiMusou13), YakuResult.doubleYakuman(YakuType.ChurenPoto9))

        assertEquals(
            listOf("mahjongcraft:riichi/yakuman/kokushi_musou_13", "mahjongcraft:riichi/yakuman/churen_poto_9"),
            registry.resolve(BuiltInRuleModuleIds.RIICHI, result),
        )
    }

    /** 沒有登記解析器的規則沒有展示理由。 */
    @Test
    fun `unregistered rule has no cues`() {
        assertTrue(registry.resolve("custom:rule", resultOf(YakuResult.yakuman(YakuType.KokushiMusou))).isEmpty())
    }

    /**
     * 以指定役種建立測試用日麻手牌價值結果。
     *
     * @param yakuResults 要放入結果的役種與翻數資料。
     * @return 翻數為役種翻數總和、符數為 0，並使用固定榮和點數的手牌結果。
     */
    private fun resultOf(vararg yakuResults: YakuResult): RiichiHandValueResult = RiichiHandValueResult(
        yakuResults = yakuResults.toList(),
        totalHan = yakuResults.sumOf(YakuResult::han),
        totalFu = 0,
        pointResult = RiichiPointResult.Ron(32_000),
    )
}
