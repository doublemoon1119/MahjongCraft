package com.doublemoon1119.mahjongcraft.example.format.network

import com.doublemoon1119.mahjongcraft.example.format.ExampleFormatIds
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameCommandDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 送出攤牌動作的網路命令。 */
@Serializable
@SerialName(ExampleFormatIds.REVEAL_HAND_COMMAND)
data object ExamplePairingRevealHandCommandDto : ExtensionGameCommandDto
