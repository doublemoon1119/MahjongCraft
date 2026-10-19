package com.doublemoon1119.mahjongcraft.example.action

import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction

/** 簡易配對麻將每局可宣告一次的示範動作。 */
data object ExamplePairingDeclarationAction : ExtensionGameAction {
    override val id: String = ExamplePairingIds.DECLARE_ACTION
}
