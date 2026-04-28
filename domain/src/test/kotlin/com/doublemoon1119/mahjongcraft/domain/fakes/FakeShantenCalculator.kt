package com.doublemoon1119.mahjongcraft.domain.fakes

import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.domain.judgment.ShantenResult

/**
 * [ShantenCalculator] 的測試用 Fake 實作。
 *
 * @param shantenToReturn 每次呼叫 `calculate` 時要回傳的向聽數（預設為 8）。
 * @param isComplete 若為 true，回傳 [ShantenResult.Complete]；若為 false，回傳 [ShantenResult.NotTenpai]。
 * @param winningTiles 當 [isComplete] 為 false 且 [shantenToReturn] 為 0 時，回傳的聽牌列表。
 */
class FakeShantenCalculator(
    private val shantenToReturn: Int = 8,
    private val isComplete: Boolean = false,
    private val winningTiles: List<Tile> = emptyList()
) : ShantenCalculator {
    override fun calculate(hand: Hand): ShantenResult {
        return when {
            isComplete -> ShantenResult.Complete
            shantenToReturn == 0 -> ShantenResult.Tenpai(winningTiles)
            else -> ShantenResult.NotTenpai(shantenToReturn)
        }
    }
}
