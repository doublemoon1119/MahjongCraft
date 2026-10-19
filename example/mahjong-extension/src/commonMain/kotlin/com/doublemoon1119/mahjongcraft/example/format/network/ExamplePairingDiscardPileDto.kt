package com.doublemoon1119.mahjongcraft.example.format.network

import com.doublemoon1119.mahjongcraft.example.format.ExampleFormatIds
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DiscardPileDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 依打出順序排列的牌河的網路格式。
 *
 * @property entries 依捨牌順序排列的牌河項目。
 */
@Serializable
@SerialName(ExampleFormatIds.DISCARD_PILE)
data class ExamplePairingDiscardPileDto(
    val entries: List<ExamplePairingDiscardEntryDto>,
) : DiscardPileDto
