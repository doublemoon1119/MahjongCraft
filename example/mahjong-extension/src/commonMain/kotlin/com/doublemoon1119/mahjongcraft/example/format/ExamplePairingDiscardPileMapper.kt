package com.doublemoon1119.mahjongcraft.example.format

import com.doublemoon1119.mahjongcraft.example.format.persistence.ExamplePairingDiscardPilePersistenceDto
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingDiscardPile
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentPersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.toPersistenceDto

/**
 * 將牌河轉成獨立的保存資料。
 *
 * @param registry 組件保存轉換。
 * @return 依捨牌順序排列的保存牌河。
 */
fun ExamplePairingDiscardPile.toPersistenceDto(registry: ComponentPersistenceRegistry): ExamplePairingDiscardPilePersistenceDto = ExamplePairingDiscardPilePersistenceDto(
    entries = entries.map { it.toPersistenceDto(registry) },
)

/**
 * 還原已保存的牌河。
 *
 * @param registry 組件保存轉換。
 * @return 保留牌面、穩定 ID 與取走標記的牌河。
 */
fun ExamplePairingDiscardPilePersistenceDto.toDomain(registry: ComponentPersistenceRegistry): ExamplePairingDiscardPile = entries.fold(ExamplePairingDiscardPile()) { pile, entry ->
    pile.discard(entry.toDomain(registry))
}
