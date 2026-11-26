package com.doublemoon1119.mahjongcraft.example.action

import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction

/** 簡易配對麻將每局可使用一次的攤牌動作。 */
data object ExamplePairingRevealHandAction : ExtensionGameAction {
    override val id: String = ExamplePairingIds.REVEAL_HAND_ACTION
}
