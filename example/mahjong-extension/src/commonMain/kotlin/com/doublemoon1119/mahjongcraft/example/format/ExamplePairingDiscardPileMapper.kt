package com.doublemoon1119.mahjongcraft.example.format

import com.doublemoon1119.mahjongcraft.example.format.network.ExamplePairingDiscardEntryDto
import com.doublemoon1119.mahjongcraft.example.format.network.ExamplePairingDiscardPileDto
import com.doublemoon1119.mahjongcraft.example.format.persistence.ExamplePairingDiscardPilePersistenceDto
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingDiscardPile
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.DiscardEntryPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile

/**
 * 將牌河轉成網路資料，保留牌面與穩定 ID。
 *
 * @return 依捨牌順序排列的網路牌河。
 */
fun ExamplePairingDiscardPile.toNetworkDto(): ExamplePairingDiscardPileDto = ExamplePairingDiscardPileDto(
    entries = entries.map { ExamplePairingDiscardEntryDto(tile = it.tile.toDto(), isTaken = it.isTaken) },
)

/**
 * 還原網路牌河。
 *
 * @return 保留取走標記的牌河。
 */
fun ExamplePairingDiscardPileDto.toDomain(): ExamplePairingDiscardPile = entries.fold(ExamplePairingDiscardPile()) { pile, entry ->
    pile.discard(DiscardPile.DiscardEntry(tile = entry.tile.toDomain(), isTaken = entry.isTaken))
}

/**
 * 將牌河轉成獨立的保存資料。
 *
 * @return 依捨牌順序排列的保存牌河。
 */
fun ExamplePairingDiscardPile.toPersistenceDto(): ExamplePairingDiscardPilePersistenceDto = ExamplePairingDiscardPilePersistenceDto(
    entries = entries.map { DiscardEntryPersistenceDto(tile = it.tile.toPersistenceDto(), isTaken = it.isTaken) },
)

/**
 * 還原已保存的牌河。
 *
 * @return 保留牌面、穩定 ID 與取走標記的牌河。
 */
fun ExamplePairingDiscardPilePersistenceDto.toDomain(): ExamplePairingDiscardPile = entries.fold(ExamplePairingDiscardPile()) { pile, entry ->
    pile.discard(DiscardPile.DiscardEntry(tile = entry.tile.toDomain(), isTaken = entry.isTaken))
}
