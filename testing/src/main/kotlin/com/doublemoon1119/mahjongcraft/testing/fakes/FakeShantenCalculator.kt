package com.doublemoon1119.mahjongcraft.testing.fakes

import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.domain.judgment.ShantenResult

/**
 * [ShantenCalculator] 的測試用 Fake 實作。
 *
 * @param shantenToReturn 每次呼叫 `calculate` 時要回傳的固定向聽數。預設為 8。
 */
class FakeShantenCalculator(
    private val shantenToReturn: Int = 8
) : ShantenCalculator {
    override fun calculate(hand: Hand): ShantenResult {
        return ShantenResult(shanten = shantenToReturn)
    }
}
