package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueCalculator
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult

/** 將合法的簡易配對牌型轉成中立胡牌結果。 */
class ExamplePairingHandValueCalculator : HandValueCalculator<ExamplePairingHandValueContext, ExamplePairingHandValueResult> {
    override fun calculate(context: ExamplePairingHandValueContext): ExamplePairingHandValueResult {
        require(ExamplePairingShantenCalculator().calculate(context.hand) is ShantenResult.Complete) {
            "Example pairing hand value requires a completed two-pair hand"
        }
        return ExamplePairingHandValueResult
    }
}
