package com.doublemoon1119.mahjongcraft.example.format.persistence

import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.DiscardEntryPersistenceDto
import kotlinx.serialization.Serializable

/**
 * 牌河的保存格式，保留每張牌的身分與是否被取走。
 *
 * @property entries 依捨牌順序排列的牌河項目。
 */
@Serializable
data class ExamplePairingDiscardPilePersistenceDto(val entries: List<DiscardEntryPersistenceDto>)
