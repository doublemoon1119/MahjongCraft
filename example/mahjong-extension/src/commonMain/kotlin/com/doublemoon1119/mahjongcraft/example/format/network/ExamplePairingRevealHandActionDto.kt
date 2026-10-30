package com.doublemoon1119.mahjongcraft.example.format.network

import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameActionDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 已成立的攤牌網路動作。 */
@Serializable
@SerialName(ExamplePairingIds.REVEAL_HAND_ACTION)
data object ExamplePairingRevealHandActionDto : ExtensionGameActionDto
