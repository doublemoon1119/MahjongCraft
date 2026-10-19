package com.doublemoon1119.mahjongcraft.example.format.network

import com.doublemoon1119.mahjongcraft.example.format.ExampleFormatIds
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExhaustiveDrawReasonDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 簡易配對牌山耗盡的網路原因。 */
@Serializable
@SerialName(ExampleFormatIds.EXHAUSTIVE_DRAW)
data object ExamplePairingExhaustiveDrawReasonDto : ExhaustiveDrawReasonDto
