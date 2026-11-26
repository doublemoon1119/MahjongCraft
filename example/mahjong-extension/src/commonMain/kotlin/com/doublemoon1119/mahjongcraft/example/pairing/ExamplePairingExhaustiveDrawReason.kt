package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason

/** 簡易配對麻將牌山耗盡時的流局原因。 */
data object ExamplePairingExhaustiveDrawReason : ExhaustiveDrawReason {
    override val id: String = ExamplePairingIds.EXHAUSTIVE_DRAW
}
