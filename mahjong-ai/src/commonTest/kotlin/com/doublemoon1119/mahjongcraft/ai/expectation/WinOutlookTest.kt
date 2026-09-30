package com.doublemoon1119.mahjongcraft.ai.expectation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證和牌機率與期望打點的遞推計算。 */
class WinOutlookTest {
    /** 十輪、每輪三張對手捨牌折半計算、每輪有 10% 機率因他家和牌而結束。 */
    private val outlook = DrawOutlook(ownDraws = 10, ronChancesPerCycle = 1.5, continuationRate = 0.9)

    /** 四張聽牌、自摸與榮和皆為 1000 點。 */
    private val profile = TenpaiProfile.of(listOf(WaitValue(count = 4, tsumoValue = 1000.0, ronValue = 1000.0)), unseenTotal = 100)

    /** 聽牌張數越多，和牌機率越高。 */
    @Test
    fun `more waiting tiles give a higher win probability`() {
        val narrow = TenpaiProfile.of(listOf(WaitValue(count = 2, tsumoValue = 1000.0, ronValue = 1000.0)), unseenTotal = 100)
        val wide = TenpaiProfile.of(listOf(WaitValue(count = 8, tsumoValue = 1000.0, ronValue = 1000.0)), unseenTotal = 100)

        assertTrue(wide.outlook(10, outlook).winProbability > narrow.outlook(10, outlook).winProbability)
    }

    /** 剩餘輪數越多，和牌機率越高；沒有剩餘輪數時不可能和牌。 */
    @Test
    fun `more remaining cycles give a higher win probability`() {
        assertTrue(profile.outlook(10, outlook).winProbability > profile.outlook(3, outlook).winProbability)
        assertEquals(WinOutlook.NONE, profile.outlook(0, outlook))
    }

    /** 本局越容易因他家和牌而結束，和牌機率越低。 */
    @Test
    fun `a round that may end early lowers the win probability`() {
        val endless = outlook.copy(continuationRate = 1.0)

        assertTrue(profile.outlook(10, outlook).winProbability < profile.outlook(10, endless).winProbability)
    }

    /** 不能榮和的聽牌只剩自摸機會，機率較低。 */
    @Test
    fun `a wait that cannot win by ron only counts self draws`() {
        val tsumoOnly = TenpaiProfile.of(listOf(WaitValue(count = 4, tsumoValue = 1000.0, ronValue = null)), unseenTotal = 100)

        assertEquals(0.0, tsumoOnly.ronRate)
        assertTrue(tsumoOnly.outlook(10, outlook).winProbability < profile.outlook(10, outlook).winProbability)
    }

    /** 期望打點依兩種機會的比例在自摸與榮和點數之間。 */
    @Test
    fun `expected value per win lies between the tsumo and ron values`() {
        val mixed = TenpaiProfile.of(listOf(WaitValue(count = 4, tsumoValue = 2000.0, ronValue = 1000.0)), unseenTotal = 100)
        val result = mixed.outlook(10, outlook)
        val valuePerWin = result.expectedValue / result.winProbability

        assertTrue(valuePerWin > 1000.0 && valuePerWin < 2000.0, "value per win $valuePerWin")
    }

    /** 一向聽的和牌機率低於同樣聽牌型直接聽牌，兩向聽再更低。 */
    @Test
    fun `each shanten step lowers the win probability`() {
        val tenpai = profile.outlook(outlook.ownDraws, outlook)
        val oneShanten = oneShantenOutlook(listOf(TenpaiBranch(drawProbability = 0.2, options = listOf(TenpaiOption(profile)))), outlook)
        val twoShanten = distantOutlook(
            shanten = 2,
            firstAdvanceRate = 0.2,
            laterAdvanceRate = 0.2,
            tenpaiProfile = profile,
            outlook = outlook,
        )

        assertTrue(oneShanten.winProbability < tenpai.winProbability)
        assertTrue(twoShanten.winProbability < oneShanten.winProbability)
        assertTrue(twoShanten.winProbability > 0.0)
    }

    /** 第一步之後的有效牌較少時，兩向聽的和牌機率較低。 */
    @Test
    fun `fewer tiles after the first step lower the win probability`() {
        val steady = distantOutlook(shanten = 2, firstAdvanceRate = 0.3, laterAdvanceRate = 0.3, tenpaiProfile = profile, outlook = outlook)
        val narrowing = distantOutlook(shanten = 2, firstAdvanceRate = 0.3, laterAdvanceRate = 0.1, tenpaiProfile = profile, outlook = outlook)

        assertTrue(narrowing.winProbability < steady.winProbability)
    }

    /** 一向聽的逐輪加總與以相同機率遞推一步的結果一致。 */
    @Test
    fun `one shanten enumeration agrees with a single recurrence step`() {
        val enumerated = oneShantenOutlook(listOf(TenpaiBranch(drawProbability = 0.2, options = listOf(TenpaiOption(profile)))), outlook)
        val recurred = distantOutlook(shanten = 1, firstAdvanceRate = 0.2, laterAdvanceRate = 0.2, tenpaiProfile = profile, outlook = outlook)

        assertEquals(recurred.winProbability, enumerated.winProbability, absoluteTolerance = 1e-9)
        assertEquals(recurred.expectedValue, enumerated.expectedValue, absoluteTolerance = 1e-6)
    }

    /** 聽牌路線依剩餘輪數選擇期望值較高的打法：輪數多時值得付出宣告成本，輪數少時默聽。 */
    @Test
    fun `a branch picks the better option for the remaining cycles`() {
        val dama = TenpaiOption(profile)
        val declared = TenpaiOption(
            profile = TenpaiProfile.of(listOf(WaitValue(count = 4, tsumoValue = 8000.0, ronValue = 8000.0)), unseenTotal = 100),
            cost = 1000.0,
        )
        val branch = TenpaiBranch(drawProbability = 0.2, options = listOf(dama, declared))

        assertEquals(declared.outlook(10, outlook), branch.outlook(10, outlook))
        assertEquals(dama.outlook(1, outlook), branch.outlook(1, outlook))
    }

    /** 宣告後不能換牌的手牌預期進行的輪數，會因聽牌寬而變少。 */
    @Test
    fun `a wider wait ends the hand sooner`() {
        val narrow = TenpaiProfile.of(listOf(WaitValue(count = 1, tsumoValue = 1000.0, ronValue = 1000.0)), unseenTotal = 100)
        val wide = TenpaiProfile.of(listOf(WaitValue(count = 8, tsumoValue = 1000.0, ronValue = 1000.0)), unseenTotal = 100)

        assertTrue(wide.expectedCyclesInPlay(10, outlook) < narrow.expectedCyclesInPlay(10, outlook))
        assertTrue(narrow.expectedCyclesInPlay(10, outlook) <= 10.0)
    }
}
