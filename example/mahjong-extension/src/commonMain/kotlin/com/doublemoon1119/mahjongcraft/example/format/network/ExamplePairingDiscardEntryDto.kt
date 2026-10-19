package com.doublemoon1119.mahjongcraft.example.format.network

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.IdentifiedTileDto
import kotlinx.serialization.Serializable

/**
 * 一筆簡易配對捨牌的網路格式。
 *
 * @property tile 被打出的牌。
 * @property isTaken 是否已被取走。
 */
@Serializable
data class ExamplePairingDiscardEntryDto(
    val tile: IdentifiedTileDto,
    val isTaken: Boolean,
)
