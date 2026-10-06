package com.doublemoon1119.mahjongcraft.ai.expectation

import kotlin.math.pow

/**
 * 一手牌在本局剩餘時間內的和牌前景。
 *
 * @property winProbability 本局內和牌的機率。
 * @property expectedValue 和牌機率乘上和牌時的平均點數，扣除聽牌時宣告的成本。
 */
internal data class WinOutlook(
    val winProbability: Double,
    val expectedValue: Double,
) {
    /** [WinOutlook] 的固定值。 */
    companion object {
        /** 不可能和牌。 */
        val NONE: WinOutlook = WinOutlook(winProbability = 0.0, expectedValue = 0.0)
    }
}

/**
 * 一張聽牌的張數與和牌點數。
 *
 * @property count 這張牌的未見張數。
 * @property tsumoValue 自摸這張牌的點數；不能自摸和牌時為 null。
 * @property ronValue 榮和這張牌的點數；不能榮和時為 null。
 */
internal data class WaitValue(
    val count: Int,
    val tsumoValue: Double?,
    val ronValue: Double?,
)

/**
 * 一手聽牌每一輪的和牌率與平均點數。
 *
 * 每一輪先有自己摸牌與對手捨牌的和牌機會；沒有和牌時，本局以 [DrawOutlook.continuationRate] 的機率進入下一輪。
 *
 * @property tsumoRate 自己每摸一張牌就自摸和牌的機率。
 * @property ronRate 對手每打出一張牌就能榮和的機率。
 * @property tsumoValue 自摸和牌的平均點數。
 * @property ronValue 榮和的平均點數。
 */
internal data class TenpaiProfile(
    val tsumoRate: Double,
    val ronRate: Double,
    val tsumoValue: Double,
    val ronValue: Double,
) {
    /** 一輪之中完全沒有和牌的機率。 */
    fun missRate(outlook: DrawOutlook): Double = (1 - tsumoRate) * (1 - ronRate).pow(outlook.ronChancesPerCycle)

    /** 剩餘 [cycles] 輪內的和牌前景。 */
    fun outlook(cycles: Int, outlook: DrawOutlook): WinOutlook {
        if (cycles <= 0) return WinOutlook.NONE
        val miss = missRate(outlook)
        val winPerCycle = 1 - miss
        if (winPerCycle <= 0) return WinOutlook.NONE
        val ronShare = (winPerCycle - tsumoRate).coerceAtLeast(0.0)
        val valuePerWin = (tsumoRate * tsumoValue + ronShare * ronValue) / winPerCycle
        val winProbability = winPerCycle * expectedCyclesInPlay(cycles, outlook)
        return WinOutlook(winProbability = winProbability, expectedValue = winProbability * valuePerWin)
    }

    /** 剩餘 [cycles] 輪內，這手牌預期還會進行幾輪才和牌或本局結束。 */
    fun expectedCyclesInPlay(cycles: Int, outlook: DrawOutlook): Double {
        val carryOver = missRate(outlook) * outlook.continuationRate
        return (0 until cycles.coerceAtLeast(0)).sumOf { carryOver.pow(it) }
    }

    /** [TenpaiProfile] 的建立方式。 */
    companion object {
        /** 沒有任何可和牌的聽牌。 */
        val NONE: TenpaiProfile = TenpaiProfile(tsumoRate = 0.0, ronRate = 0.0, tsumoValue = 0.0, ronValue = 0.0)

        /** 由每一張聽牌的張數與點數，在總共 [unseenTotal] 張未見牌中計算。 */
        fun of(waits: List<WaitValue>, unseenTotal: Int): TenpaiProfile {
            if (unseenTotal <= 0) return NONE
            val tsumoWaits = waits.filter { it.tsumoValue != null && it.count > 0 }
            val ronWaits = waits.filter { it.ronValue != null && it.count > 0 }
            val tsumoCount = tsumoWaits.sumOf { it.count }
            val ronCount = ronWaits.sumOf { it.count }
            return TenpaiProfile(
                tsumoRate = tsumoCount.toDouble() / unseenTotal,
                ronRate = ronCount.toDouble() / unseenTotal,
                tsumoValue = if (tsumoCount == 0) 0.0 else tsumoWaits.sumOf { it.count * checkNotNull(it.tsumoValue) } / tsumoCount,
                ronValue = if (ronCount == 0) 0.0 else ronWaits.sumOf { it.count * checkNotNull(it.ronValue) } / ronCount,
            )
        }
    }
}

/**
 * 聽牌時的一種打法，例如不宣告，或宣告某個動作。
 *
 * @property profile 採用這種打法時的和牌率與點數。
 * @property cost 採用這種打法時立即付出的點數。
 */
internal data class TenpaiOption(
    val profile: TenpaiProfile,
    val cost: Double = 0.0,
) {
    /** 剩餘 [cycles] 輪內的和牌前景，期望值扣除 [cost]。 */
    fun outlook(cycles: Int, outlook: DrawOutlook): WinOutlook {
        val result = profile.outlook(cycles, outlook)
        return result.copy(expectedValue = result.expectedValue - cost)
    }
}

/**
 * 一向聽手牌摸進某種牌後聽牌的一條路線。
 *
 * @property drawProbability 每次摸牌摸到這種牌的機率。
 * @property options 摸進這種牌並打出最佳的牌後，聽牌時可選的打法；每次依剩餘輪數選擇期望值最高者。
 */
internal data class TenpaiBranch(
    val drawProbability: Double,
    val options: List<TenpaiOption>,
) {
    /** 剩餘 [cycles] 輪內，以最佳打法計算的和牌前景。 */
    fun outlook(cycles: Int, outlook: DrawOutlook): WinOutlook = options
        .map { it.outlook(cycles, outlook) }
        .maxByOrNull { it.expectedValue }
        ?.takeIf { it.expectedValue > 0 }
        ?: WinOutlook.NONE
}

/**
 * 一向聽的和牌前景：第 i 輪才摸到聽牌的機率，乘上該路線在剩餘輪數內的和牌前景，逐輪加總。
 */
internal fun oneShantenOutlook(branches: List<TenpaiBranch>, outlook: DrawOutlook): WinOutlook {
    val advanceRate = branches.sumOf { it.drawProbability }
    if (advanceRate <= 0) return WinOutlook.NONE
    var winProbability = 0.0
    var expectedValue = 0.0
    for (cycle in 1..outlook.ownDraws) {
        val reachesCycle = ((1 - advanceRate) * outlook.continuationRate).pow(cycle - 1)
        branches.forEach { branch ->
            val afterTenpai = branch.outlook(cycles = outlook.ownDraws - cycle, outlook = outlook)
            val weight = reachesCycle * branch.drawProbability * outlook.continuationRate
            winProbability += weight * afterTenpai.winProbability
            expectedValue += weight * afterTenpai.expectedValue
        }
    }
    return WinOutlook(winProbability = winProbability, expectedValue = expectedValue)
}

/**
 * 兩向聽以上的和牌前景。
 *
 * 從目前的向聽前進一步的機率為 [firstAdvanceRate]，之後每一步為 [laterAdvanceRate]，逐步遞推到聽牌；
 * 聽牌之後以 [tenpaiProfile] 計算和牌。
 *
 * @param shanten 目前的向聽數。
 * @param firstAdvanceRate 目前的手牌每次摸牌前進一向聽的機率。
 * @param laterAdvanceRate 之後的手牌每次摸牌前進一向聽的機率。
 * @param tenpaiProfile 推算到聽牌後使用的和牌率與點數。
 */
internal fun distantOutlook(
    shanten: Int,
    firstAdvanceRate: Double,
    laterAdvanceRate: Double,
    tenpaiProfile: TenpaiProfile,
    outlook: DrawOutlook,
): WinOutlook {
    val cycles = outlook.ownDraws
    var previous = (0..cycles).map { tenpaiProfile.outlook(cycles = it, outlook = outlook) }
    for (step in 1..shanten) {
        val advanceRate = if (step == shanten) firstAdvanceRate else laterAdvanceRate
        val current = MutableList(cycles + 1) { WinOutlook.NONE }
        for (remaining in 1..cycles) {
            val advanced = previous[remaining - 1]
            val stayed = current[remaining - 1]
            val continuation = outlook.continuationRate
            current[remaining] = WinOutlook(
                winProbability = continuation * (advanceRate * advanced.winProbability + (1 - advanceRate) * stayed.winProbability),
                expectedValue = continuation * (advanceRate * advanced.expectedValue + (1 - advanceRate) * stayed.expectedValue),
            )
        }
        previous = current
    }
    return previous[cycles]
}
